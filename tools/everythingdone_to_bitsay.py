#!/usr/bin/env python3
"""
完事儿 (EverythingDone) -> bitsay 笔记迁移工具.

读入 EverythingDone 的 SQLite 库 (things 表), 按 bitsay 的备份格式
(BackupCodec: "BSB1" + varint/svarint + gzip) 生成可直接导入的
.bitsay.gz 文件, 同时输出一份人类可读的 JSON 供核对.

字段映射 (依据反编译 com.ywwynm.everythingdone.b.b 与 model.Thing):
  type  0=note 1=reminder 2=habit 3=goal, 4..8=应用自动生成的欢迎条目, -1=内置彩蛋
  state 0=underway 1=finished 2=deleted (用户选择全部保留)
  文本  = title + "\\n" + content (绝大多数只有 content)
  时间  = create_time / update_time (毫秒)

输出统一为 bitsay 的笔记 (kind=0, done=0).

用法:
  python3 tools/everythingdone_to_bitsay.py <EverythingDoneData.db> [输出目录] [--exported-at <毫秒>]
"""

import gzip
import json
import sqlite3
import sys
import time
from pathlib import Path

# 与 BackupCodec.kt 严格对齐
MAGIC = b"BSB1"
SCHEMA = 1
FLAG_DONE = 1
FLAG_HAS_UPDATED = 1 << 1
FLAG_HAS_DONE_AT = 1 << 2
FLAG_TODO = 1 << 3
MAX_TEXT_LENGTH = 20_000  # ItemRepository.MAX_TEXT_LENGTH

# 应用自动生成的欢迎/占位条目, 不是用户的笔记
HELPER_TYPES = {4, 5, 6, 7, 8, -1}


# --------------------------------------------------------------------- varint

def uvarint(value: int) -> bytes:
    """无符号 LEB128. 负数按补码走 10 字节. 与 Kotlin 的 `v ushr 7` 一致."""
    if value < 0:
        value &= (1 << 64) - 1
    out = bytearray()
    while True:
        chunk = value & 0x7F
        value >>= 7
        if value == 0:
            out.append(chunk)
            return bytes(out)
        out.append(chunk | 0x80)


def svarint(value: int) -> bytes:
    """Zigzag: 小负数也保持小体积."""
    return uvarint((value << 1) ^ (value >> 63))


# ------------------------------------------------------------------- 读取源库

def load_notes(db_path: Path):
    if not db_path.is_file():
        sys.exit(f"找不到数据库: {db_path}\n"
                 f"先把完事儿的备份解包: unzip -o ED_backup_*.bak -d <目录>")
    try:
        con = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
        con.row_factory = sqlite3.Row
        rows = con.execute(
            "SELECT id, type, state, title, content, create_time, update_time "
            "FROM things ORDER BY create_time ASC, id ASC"
        ).fetchall()
    except sqlite3.DatabaseError as exc:
        sys.exit(f"读不了这个库: {exc}\n确认它是完事儿备份里解出来的 EverythingDoneData.db")
    finally:
        con.close()

    notes, skipped = [], []
    for r in rows:
        if r["type"] in HELPER_TYPES:
            skipped.append((r["id"], r["type"], "应用自动生成的条目"))
            continue
        # 与 App 端 ItemRepository.normalize 保持一致: trim 后为空则丢弃
        text = "\n".join(
            part.strip() for part in (r["title"] or "", r["content"] or "") if part.strip()
        )
        text = text.strip()
        if not text:
            skipped.append((r["id"], r["type"], "文本为空"))
            continue
        if len(text) > MAX_TEXT_LENGTH:
            skipped.append((r["id"], r["type"], f"超长 {len(text)} 字符, 已截断"))
            text = text[:MAX_TEXT_LENGTH]

        created = int(r["create_time"] or 0)
        updated = int(r["update_time"] or 0) or created
        notes.append({
            "srcId": r["id"],
            "state": r["state"],
            "text": text,
            "createdAt": created,
            "updatedAt": updated,
        })
    return notes, skipped


# --------------------------------------------------------------- 编码 .bitsay

def encode(notes, exported_at: int) -> bytes:
    """按 BackupCodec.encode 生成 payload. id 重新连续编号, 时间按升序做 delta."""
    out = bytearray()
    out += MAGIC
    out += uvarint(SCHEMA)
    out += uvarint(exported_at)
    out += uvarint(len(notes))

    prev_id = 0
    prev_created = 0
    for index, note in enumerate(notes, start=1):
        new_id = index
        out += uvarint(new_id - prev_id)
        prev_id = new_id

        flags = 0
        if note["updatedAt"] != note["createdAt"]:
            flags |= FLAG_HAS_UPDATED
        # kind=NOTE, done=False -> FLAG_DONE / FLAG_TODO / FLAG_HAS_DONE_AT 都不置位
        out += uvarint(flags)

        out += svarint(note["createdAt"] - prev_created)
        prev_created = note["createdAt"]

        if note["updatedAt"] != note["createdAt"]:
            out += svarint(note["updatedAt"] - note["createdAt"])

        raw = note["text"].encode("utf-8")
        out += uvarint(len(raw))
        out += raw
    return bytes(out)


# ------------------------------------------------------- 独立解码器 (用于自检)

class _Reader:
    def __init__(self, data: bytes):
        self.data = data
        self.pos = 0

    def remaining(self):
        return len(self.data) - self.pos

    def take(self, n):
        if n < 0 or n > self.remaining():
            raise ValueError("文件被截断")
        chunk = self.data[self.pos:self.pos + n]
        self.pos += n
        return chunk

    def uvarint(self):
        result, shift = 0, 0
        while True:
            if self.pos >= len(self.data):
                raise ValueError("文件被截断")
            byte = self.data[self.pos]
            self.pos += 1
            result |= (byte & 0x7F) << shift
            if not byte & 0x80:
                return result
            shift += 7
            if shift > 63:
                raise ValueError("数字编码异常")

    def svarint(self):
        raw = self.uvarint()
        return (raw >> 1) ^ -(raw & 1)


def decode(payload: bytes):
    """刻意独立实现, 用来交叉验证编码结果能被 App 正确读回."""
    r = _Reader(payload)
    if r.take(4) != MAGIC:
        raise ValueError("magic 不匹配")
    schema = r.uvarint()
    if schema > SCHEMA:
        raise ValueError(f"schema 过新: {schema}")
    exported_at = r.uvarint()
    count = r.uvarint()
    if count < 0 or count > r.remaining():
        raise ValueError("count 非法")

    items, prev_id, prev_created = [], 0, 0
    for _ in range(count):
        item_id = prev_id + r.uvarint()
        prev_id = item_id
        flags = r.uvarint()
        created = prev_created + r.svarint()
        prev_created = created
        if flags & FLAG_HAS_UPDATED:
            updated = created + r.svarint()
        else:
            updated = created
        done_at = created + r.svarint() if flags & FLAG_HAS_DONE_AT else None
        length = r.uvarint()
        if length < 0 or length > (1 << 20):
            raise ValueError("长度非法")
        text = r.take(length).decode("utf-8")
        if text.strip():
            items.append({
                "id": item_id,
                "kind": 1 if flags & FLAG_TODO else 0,
                "text": text,
                "done": bool(flags & FLAG_DONE),
                "createdAt": created,
                "updatedAt": updated,
                "doneAt": done_at,
            })
    if r.remaining() != 0:
        raise ValueError(f"payload 末尾还有 {r.remaining()} 字节残留")
    return {"schema": schema, "exportedAt": exported_at, "items": items}


# ------------------------------------------------------------------------ main

def main():
    args = [a for a in sys.argv[1:]]
    exported_at = None
    if "--exported-at" in args:
        index = args.index("--exported-at")
        try:
            exported_at = int(args[index + 1])
        except (IndexError, ValueError):
            sys.exit("--exported-at 需要一个毫秒时间戳")
        del args[index:index + 2]

    src = Path(args[0] if args else "extracted/databases/EverythingDoneData.db")
    out_dir = Path(args[1] if len(args) > 1 else "out")
    out_dir.mkdir(parents=True, exist_ok=True)

    notes, skipped = load_notes(src)
    # exportedAt 只是备份的元数据. 默认取当前时间; 传 --exported-at 可让输出字节级可复现.
    if exported_at is None:
        exported_at = int(time.time() * 1000)

    payload = encode(notes, exported_at)
    archive = gzip.compress(payload, compresslevel=9, mtime=0)  # mtime=0 保证可复现

    gz_path = out_dir / "everythingdone.bitsay.gz"
    gz_path.write_bytes(archive)

    json_path = out_dir / "everythingdone-notes.json"
    json_path.write_text(
        json.dumps(
            {
                "source": "com.ywwynm.everythingdone (完事儿)",
                "sourceDb": str(src),
                "exportedAt": exported_at,
                "count": len(notes),
                "skipped": [{"id": i, "type": t, "why": w} for i, t, w in skipped],
                "notes": notes,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )

    # 自检: 用独立解码器读回, 逐条比对
    decoded = decode(gzip.decompress(archive))
    assert decoded["schema"] == SCHEMA, "schema 不符"
    assert len(decoded["items"]) == len(notes), \
        f"条数不符: 解码 {len(decoded['items'])} vs 原始 {len(notes)}"
    for got, want in zip(decoded["items"], notes):
        assert got["text"] == want["text"], f"文本不符 @ id {got['id']}"
        assert got["createdAt"] == want["createdAt"], f"创建时间不符 @ id {got['id']}"
        assert got["updatedAt"] == want["updatedAt"], f"修改时间不符 @ id {got['id']}"
        assert got["kind"] == 0 and got["done"] is False, f"类型不符 @ id {got['id']}"

    by_state = {}
    for n in notes:
        by_state[n["state"]] = by_state.get(n["state"], 0) + 1

    print(f"源库      : {src}")
    print(f"导入条目  : {len(notes)}  (state 分布 {by_state})")
    print(f"跳过      : {len(skipped)}")
    for i, t, w in skipped:
        print(f"            - id={i} type={t} : {w}")
    print(f"payload   : {len(payload)} 字节 -> gzip {len(archive)} 字节")
    print(f"备份文件  : {gz_path}")
    print(f"JSON 副本 : {json_path}")
    print("自检      : 通过 (独立解码器逐条比对文本与时间戳)")


if __name__ == "__main__":
    main()
