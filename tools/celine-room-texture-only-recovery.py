#!/usr/bin/env python3
"""Create geometry-preserving Room recovery GLBs by resizing embedded source textures only.

The canonical 12 source-of-origin GLBs remain immutable. This tool writes derived working
copies whose non-image bufferViews are byte-for-byte identical to the source. It exists to
separate texture payload reduction from geometry simplification while recovering Celine's room.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import struct
from pathlib import Path

from PIL import Image

JSON_CHUNK = 0x4E4F534A
BIN_CHUNK = 0x004E4942
GLB_MAGIC = b"glTF"


def align4(data: bytes, pad: bytes = b"\x00") -> bytes:
    return data + pad * ((-len(data)) % 4)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def read_glb(path: Path) -> tuple[dict, bytes]:
    raw = path.read_bytes()
    if len(raw) < 20 or raw[:4] != GLB_MAGIC:
        raise ValueError(f"invalid GLB magic: {path}")
    version, declared = struct.unpack_from("<II", raw, 4)
    if version != 2 or declared != len(raw):
        raise ValueError(f"invalid GLB header: {path} version={version} declared={declared} actual={len(raw)}")

    offset = 12
    document = None
    binary = None
    while offset + 8 <= len(raw):
        length, chunk_type = struct.unpack_from("<II", raw, offset)
        offset += 8
        payload = raw[offset:offset + length]
        offset += length
        if len(payload) != length:
            raise ValueError(f"truncated GLB chunk: {path}")
        if chunk_type == JSON_CHUNK:
            document = json.loads(payload.rstrip(b" \t\r\n\x00").decode("utf-8"))
        elif chunk_type == BIN_CHUNK:
            binary = payload
    if document is None or binary is None:
        raise ValueError(f"GLB must contain JSON and BIN chunks: {path}")
    if len(document.get("buffers", [])) != 1:
        raise ValueError(f"expected one embedded GLB buffer: {path}")
    return document, binary


def buffer_view_bytes(document: dict, binary: bytes, index: int) -> bytes:
    view = document["bufferViews"][index]
    if int(view.get("buffer", 0)) != 0:
        raise ValueError(f"external/nonzero buffer in bufferView {index}")
    start = int(view.get("byteOffset", 0))
    length = int(view["byteLength"])
    end = start + length
    payload = binary[start:end]
    if len(payload) != length:
        raise ValueError(f"bufferView {index} exceeds BIN chunk")
    return payload


def resize_image(payload: bytes, mime: str, max_edge: int) -> tuple[bytes, dict]:
    with Image.open(io.BytesIO(payload)) as image:
        image.load()
        before = [int(image.width), int(image.height)]
        if max(before) <= max_edge:
            return payload, {"before": before, "after": before, "resized": False}
        scale = max_edge / float(max(before))
        size = (max(1, round(image.width * scale)), max(1, round(image.height * scale)))
        resized = image.resize(size, Image.Resampling.LANCZOS)
        out = io.BytesIO()
        normalized = mime.lower()
        if normalized == "image/jpeg":
            if resized.mode not in ("RGB", "L"):
                resized = resized.convert("RGB")
            resized.save(out, format="JPEG", quality=90, optimize=True, progressive=False)
        elif normalized == "image/png":
            resized.save(out, format="PNG", optimize=True, compress_level=9)
        elif normalized == "image/webp":
            resized.save(out, format="WEBP", quality=90, method=6)
        else:
            raise ValueError(f"unsupported embedded image MIME type: {mime}")
        return out.getvalue(), {"before": before, "after": [size[0], size[1]], "resized": True}


def write_glb(path: Path, document: dict, binary: bytes) -> None:
    json_bytes = json.dumps(document, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    json_chunk = align4(json_bytes, b" ")
    bin_chunk = align4(binary, b"\x00")
    total = 12 + 8 + len(json_chunk) + 8 + len(bin_chunk)
    header = GLB_MAGIC + struct.pack("<II", 2, total)
    path.write_bytes(
        header
        + struct.pack("<II", len(json_chunk), JSON_CHUNK)
        + json_chunk
        + struct.pack("<II", len(bin_chunk), BIN_CHUNK)
        + bin_chunk
    )


def optimize_one(source: Path, target: Path, max_edge: int) -> dict:
    document, binary = read_glb(source)
    views = document.get("bufferViews", [])
    images = document.get("images", [])

    image_view_mimes: dict[int, str] = {}
    for image in images:
        if "bufferView" not in image:
            raise ValueError(f"external/data-URI image is not allowed in canonical source GLB: {source.name}")
        index = int(image["bufferView"])
        mime = str(image.get("mimeType", ""))
        existing = image_view_mimes.get(index)
        if existing is not None and existing != mime:
            raise ValueError(f"shared image bufferView {index} has conflicting MIME types in {source.name}")
        image_view_mimes[index] = mime

    original_non_image = {
        index: sha256(buffer_view_bytes(document, binary, index))
        for index in range(len(views))
        if index not in image_view_mimes
    }

    image_payloads: dict[int, bytes] = {}
    image_report = []
    for index, mime in sorted(image_view_mimes.items()):
        payload = buffer_view_bytes(document, binary, index)
        replacement, metrics = resize_image(payload, mime, max_edge)
        image_payloads[index] = replacement
        image_report.append(
            {
                "bufferView": index,
                "mimeType": mime,
                "sourceBytes": len(payload),
                "derivedBytes": len(replacement),
                **metrics,
            }
        )

    new_bin = bytearray()
    for index, view in enumerate(views):
        payload = image_payloads.get(index)
        if payload is None:
            payload = buffer_view_bytes(document, binary, index)
        while len(new_bin) % 4:
            new_bin.append(0)
        view["byteOffset"] = len(new_bin)
        view["byteLength"] = len(payload)
        new_bin.extend(payload)

    document["buffers"][0]["byteLength"] = len(new_bin)
    target.parent.mkdir(parents=True, exist_ok=True)
    write_glb(target, document, bytes(new_bin))

    check_doc, check_bin = read_glb(target)
    if len(check_doc.get("bufferViews", [])) != len(views):
        raise ValueError(f"bufferView count changed for {source.name}")
    for index, expected in original_non_image.items():
        actual = sha256(buffer_view_bytes(check_doc, check_bin, index))
        if actual != expected:
            raise ValueError(f"non-image bufferView {index} changed for {source.name}")

    return {
        "name": source.name,
        "sourceBytes": source.stat().st_size,
        "derivedBytes": target.stat().st_size,
        "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "derivedSha256": hashlib.sha256(target.read_bytes()).hexdigest(),
        "bufferViews": len(views),
        "images": len(images),
        "nonImageBufferViewsVerifiedByteIdentical": len(original_non_image),
        "imageReport": image_report,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--max-edge", type=int, default=1024)
    args = parser.parse_args()

    if not 256 <= args.max_edge <= 4096:
        raise SystemExit("--max-edge must be between 256 and 4096")
    sources = sorted(args.source_dir.glob("*.glb"))
    if len(sources) != 12:
        raise SystemExit(f"expected exactly 12 canonical source GLBs, got {len(sources)}")

    args.output_dir.mkdir(parents=True, exist_ok=True)
    reports = []
    for source in sources:
        target = args.output_dir / source.name
        report = optimize_one(source, target, args.max_edge)
        reports.append(report)
        print(
            f"texture-only recovery {source.name}: "
            f"{report['sourceBytes']} -> {report['derivedBytes']} bytes; "
            f"non-image bufferViews verified={report['nonImageBufferViewsVerifiedByteIdentical']}"
        )

    manifest = {
        "schema": 1,
        "purpose": "ROOM recovery working copies; texture payload reduction only",
        "sourceMutation": False,
        "geometryPolicy": "all non-image bufferViews byte-for-byte identical",
        "textureMaxEdge": args.max_edge,
        "files": reports,
        "sourceTotalBytes": sum(item["sourceBytes"] for item in reports),
        "derivedTotalBytes": sum(item["derivedBytes"] for item in reports),
    }
    args.manifest.parent.mkdir(parents=True, exist_ok=True)
    args.manifest.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("CELINE_ROOM_TEXTURE_ONLY_RECOVERY PASS")
    print(f"sourceTotalBytes={manifest['sourceTotalBytes']}")
    print(f"derivedTotalBytes={manifest['derivedTotalBytes']}")


if __name__ == "__main__":
    main()
