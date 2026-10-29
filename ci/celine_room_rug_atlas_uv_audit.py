#!/usr/bin/env python3
"""Read-only actual indexed rug glTF baseColor atlas/UV spatial audit.

Structural evidence only; samples baseColor, metallic-roughness and normal-map\ntexels by indexed triangle UV centroids. This never implies Android or whole-room
visual acceptance. Source identity is pinned independently of the optimizer.
"""
import argparse
import hashlib
import io
import json
import math
from pathlib import Path
from collections import defaultdict

from PIL import Image, ImageChops, ImageFilter, ImageStat
from celine_room_rug_source_derived_audit import (
    SOURCE_SHA, SOURCE_SIZE, accessor, load,
)

REPEAT = 10497
CLAMP = 33071
MIRROR = 33648
CELL_COUNT = 4
SAMPLE_TRIANGLES = 24000


def wrap_uv(v, mode):
    if not math.isfinite(v):
        raise ValueError("Nonfinite texture coordinate")
    if mode == REPEAT:
        return v % 1.0
    if mode == CLAMP:
        return min(1.0 - 1e-12, max(0.0, v))
    if mode == MIRROR:
        remainder = v % 2.0
        return remainder if remainder <= 1.0 else 2.0 - remainder
    raise ValueError(f"Unsupported glTF sampler wrap: {mode}")


def image_bytes(raw, j, binary_offset, binary_length, image_index):
    image = j["images"][image_index]
    if "bufferView" not in image:
        raise ValueError("External image not allowed in self-contained room GLB")
    view = j["bufferViews"][image["bufferView"]]
    if view.get("buffer", 0) != 0:
        raise ValueError("External image buffer")
    start = binary_offset + view.get("byteOffset", 0)
    end = start + view["byteLength"]
    if start < binary_offset or end > binary_offset + binary_length:
        raise ValueError("Embedded image outside GLB BIN chunk")
    return raw[start:end]


def describe_texture(j, raw, binary_offset, binary_length, material_index, cache, role):
    material = j["materials"][material_index]
    pbr = material.get("pbrMetallicRoughness", {})
    factor = pbr.get("baseColorFactor", [1.0, 1.0, 1.0, 1.0])
    if len(factor) != 4:
        raise ValueError("Invalid baseColorFactor")
    reference = (pbr.get("baseColorTexture") if role == "baseColor" else
                 pbr.get("metallicRoughnessTexture") if role == "metallicRoughness" else
                 material.get("normalTexture") if role == "normal" else None)
    if role not in ("baseColor", "metallicRoughness", "normal"):
        raise ValueError("Unrecognized glTF PBR texture role")
    if not reference:
        return {"texture": False, "factor": factor, "uv_set": None,
                "texture_size": None, "sampler": None, "embedded_image_sha256": None}, None
    tex = j["textures"][reference["index"]]
    image_index = tex["source"]
    sampler = j.get("samplers", [])[tex["sampler"]] if "sampler" in tex else {}
    if image_index not in cache:
        blob = image_bytes(raw, j, binary_offset, binary_length, image_index)
        with Image.open(io.BytesIO(blob)) as opened:
            img = opened.convert("RGB")
        cache[image_index] = (img, hashlib.sha256(blob).hexdigest())
    image, digest = cache[image_index]
    meta = {"texture": True, "factor": factor, "uv_set": reference.get("texCoord", 0),
            "texture_size": [image.width, image.height],
            "sampler": {"wrapS": sampler.get("wrapS", REPEAT),
                        "wrapT": sampler.get("wrapT", REPEAT),
                        "magFilter": sampler.get("magFilter"),
                        "minFilter": sampler.get("minFilter")},
            "embedded_image_sha256": digest}
    return meta, image


def profile(path, role="baseColor"):
    raw, j, binary_offset, binary_length = load(path)
    images = {}
    mats = {}
    triangles = []
    sampled_primitives = []
    for mesh_idx, mesh in enumerate(j.get("meshes", [])):
        for primitive_idx, prim in enumerate(mesh.get("primitives", [])):
            if prim.get("mode", 4) != 4 or "POSITION" not in prim.get("attributes", {}):
                raise ValueError("Rug expected indexed triangles with POSITION")
            material_idx = prim.get("material", 0)
            if material_idx not in mats:
                mats[material_idx] = describe_texture(
                    j, raw, binary_offset, binary_length, material_idx, images, role
                )
            material, image = mats[material_idx]
            pcount, pa, position = accessor(
                j, raw, binary_offset, binary_length, prim["attributes"]["POSITION"]
            )
            indexed = accessor(j, raw, binary_offset, binary_length, prim["indices"]) if "indices" in prim else None
            index_count = indexed[0] if indexed else pcount
            if index_count % 3:
                raise ValueError("Non-triangular indexed positions")
            uv_reader = None
            if material["texture"]:
                uv_name = "TEXCOORD_" + str(material["uv_set"])
                if uv_name not in prim["attributes"]:
                    raise ValueError(f"Material uses missing {uv_name}")
                uv_count, ua, uv_reader = accessor(
                    j, raw, binary_offset, binary_length, prim["attributes"][uv_name]
                )
                if ua["type"] != "VEC2" or uv_count < pcount:
                    raise ValueError("UV accessor must be VEC2 covering POSITION")
            total = index_count // 3
            stride = max(1, math.ceil(total / SAMPLE_TRIANGLES))
            included = 0
            for ti in range(0, total, stride):
                ids = [int(indexed[2](ti*3+k)[0]) if indexed else ti*3+k for k in range(3)]
                ps = [position(i) for i in ids]
                cx = sum(p[0] for p in ps) / 3.0
                cz = sum(p[2] for p in ps) / 3.0
                uv = (sum(uv_reader(i)[0] for i in ids)/3.0,
                      sum(uv_reader(i)[1] for i in ids)/3.0) if uv_reader else (None, None)
                if not all(math.isfinite(v) for v in (cx,cz)):
                    raise ValueError("Nonfinite local sampled rug centroid")
                triangles.append((cx, cz, uv, material_idx))
                included += 1
            sampled_primitives.append({"mesh": mesh_idx, "primitive": primitive_idx,
                                       "material": material_idx, "triangles": total,
                                       "sampled_triangles": included, "sample_stride": stride})
    if not triangles:
        raise ValueError("No rug triangles sampled")
    xs = [t[0] for t in triangles]
    zs = [t[1] for t in triangles]
    extents = [min(xs),max(xs),min(zs),max(zs)]
    if extents[1] <= extents[0] or extents[3] <= extents[2]:
        raise ValueError("Degenerate sampled local rug XZ domain")
    cells = defaultdict(lambda: {"count": 0, "sum": [0.0,0.0,0.0], "tinted_sum":[0.0,0.0,0.0],
                                "uv_min":[float("inf"),float("inf")],
                                "uv_max":[-float("inf"),-float("inf")]})
    out_of_range_uv = 0
    for cx,cz,uv,mat_index in triangles:
        meta, img = mats[mat_index]
        xcell = min(CELL_COUNT-1,max(0, int(CELL_COUNT*(cx-extents[0])/(extents[1]-extents[0]))))
        zcell = min(CELL_COUNT-1,max(0, int(CELL_COUNT*(cz-extents[2])/(extents[3]-extents[2]))))
        key = f"x{xcell}_z{zcell}"
        col = cells[key]
        factor = meta["factor"]
        if img:
            u,v = uv
            if not (0 <= u <= 1 and 0 <= v <= 1):
                out_of_range_uv += 1
            col["uv_min"] = [min(col["uv_min"][0],u),min(col["uv_min"][1],v)]
            col["uv_max"] = [max(col["uv_max"][0],u),max(col["uv_max"][1],v)]
            sampler = meta["sampler"]
            # glTF normalized UV (0,0) maps to top-left image pixel.
            px = min(img.width-1, int(wrap_uv(u,sampler["wrapS"])*img.width))
            py = min(img.height-1, int(wrap_uv(v,sampler["wrapT"])*img.height))
            rgb = img.getpixel((px,py))
        else:
            rgb = (255,255,255)
        col["count"] += 1
        for k in range(3):
            col["sum"][k] += rgb[k]
            col["tinted_sum"][k] += rgb[k]*factor[k]
    result_cells = {}
    for key, item in sorted(cells.items()):
        count = item["count"]
        result_cells[key] = {
            "samples": count,
            "raw_atlas_srgb_mean": [round(a/count,3) for a in item["sum"]],
            "base_factor_weighted_srgb_mean_not_linear_pbr": [round(a/count,3) for a in item["tinted_sum"]],
            "uv_bounds": None if item["uv_min"][0] == float("inf") else [
                [round(v,6) for v in item["uv_min"]],
                [round(v,6) for v in item["uv_max"]]],
        }
    return {
        "glb_sha256": hashlib.sha256(raw).hexdigest(),
        "texture_role": role,
        "glb_bytes": len(raw), "sampled_indexed_triangles": len(triangles),
        "sampled_xz_domain_m": [round(v,6) for v in extents],
        "sampled_uv_outside_0_1_count": out_of_range_uv,
        "materials": {str(i): meta for i,(meta,img) in mats.items()},
        "sampled_primitives": sampled_primitives,
        "xz_normalized_4x4_color_cells": result_cells,
    }


def compare(source, derived):
    pairs = []
    sc = source["xz_normalized_4x4_color_cells"]
    dc = derived["xz_normalized_4x4_color_cells"]
    for key in sorted(set(sc) & set(dc)):
        a, b = sc[key], dc[key]
        if min(a["samples"],b["samples"]) < 20:
            continue
        differences = [
            round(b["base_factor_weighted_srgb_mean_not_linear_pbr"][i] -
                  a["base_factor_weighted_srgb_mean_not_linear_pbr"][i],3)
            for i in range(3)
        ]
        pairs.append({"cell":key, "source_n":a["samples"],"derived_n":b["samples"],
                      "derived_minus_source_srgb_factor_weighted": differences,
                      "mean_absolute_channel_delta": round(sum(abs(x) for x in differences)/3,3)})
    return {
        "matched_spatial_cells_at_least_20_samples_each":len(pairs),
        "cells": pairs,
        "max_mean_absolute_channel_delta":max((p["mean_absolute_channel_delta"] for p in pairs), default=None),
        "limitation":"UV-centroid + nearest embedded texel sampling, non-linear sRGB. Uniform indexed-stride sampling and separately normalized local XZ extents are not image-space registered or area-weighted. Different triangulation, texture atlases, illumination, mip/filtering or camera can change an Android raster. Neither spatial parity nor causal visual fidelity is proven from these numbers alone."
    }



def _unique_pbr_atlases(path):
    """Resolve each embedded image by *PBR role*, not unreliable glTF image index."""
    raw, gltf, binary_offset, binary_length = load(path)
    result = {}
    for role in ("baseColor", "metallicRoughness", "normal"):
        cache = {}
        roles = {}
        for index in range(len(gltf.get("materials", []))):
            meta, rgb = describe_texture(
                gltf, raw, binary_offset, binary_length, index, cache, role
            )
            if rgb is not None:
                roles[meta["embedded_image_sha256"]] = rgb
        result[role] = roles
    return result


def _mean_abs_rgb(a, b):
    if a.mode != "RGB" or b.mode != "RGB" or a.size != b.size:
        raise ValueError("PBR atlas comparison requires aligned RGB image dimensions")
    return [round(v, 4) for v in ImageStat.Stat(ImageChops.difference(a, b)).mean]


def _highpass_rgb(image, gaussian_radius_px):
    blurred = image.filter(ImageFilter.GaussianBlur(radius=gaussian_radius_px))
    return _mean_abs_rgb(image, blurred)


def _pbr_atlas_detail_audit(source_path, packaged_path):
    """Measure atlas-scale detail only; no claim about UV-visible pixels or Android."""
    original = _unique_pbr_atlases(source_path)
    derived = _unique_pbr_atlases(packaged_path)
    measurements = {}
    for role in ("baseColor", "metallicRoughness", "normal"):
        src = original[role]
        dst = derived[role]
        if len(src) != 1 or len(dst) != 1:
            measurements[role] = {
                "pairing": "UNPAIRED_MULTIPLE_OR_MISSING_ROLE_IMAGES",
                "source_unique_images": len(src),
                "packaged_unique_images": len(dst),
                "causal_verdict": "UNASSESSED",
            }
            continue
        src_img = next(iter(src.values()))
        dst_img = next(iter(dst.values()))
        reference_at_packaged_resolution = src_img.resize(
            dst_img.size, Image.Resampling.LANCZOS
        )
        source_reconstructed_at_native_resolution = reference_at_packaged_resolution.resize(
            src_img.size, Image.Resampling.LANCZOS
        )
        source_common_hf = _highpass_rgb(reference_at_packaged_resolution, 1.0)
        packaged_common_hf = _highpass_rgb(dst_img, 1.0)
        measurements[role] = {
            "pairing": "UNIQUE_ROLE_IMAGES_ATLAS_COORDINATES_NOT_PROVEN_REGISTERED",
            "source_resolution": list(src_img.size),
            "packaged_resolution": list(dst_img.size),
            "source_native_highpass_radius_2px_mae_rgb": _highpass_rgb(src_img, 2.0),
            "source_detail_not_reconstructable_after_lanczos_resize_mae_rgb":
                _mean_abs_rgb(src_img, source_reconstructed_at_native_resolution),
            "source_downsampled_vs_packaged_at_same_resolution_mae_rgb":
                _mean_abs_rgb(reference_at_packaged_resolution, dst_img),
            "source_downsampled_highpass_radius_1px_mae_rgb": source_common_hf,
            "packaged_highpass_radius_1px_mae_rgb": packaged_common_hf,
            "packaged_to_source_downsampled_highpass_ratio_rgb": [
                round(packaged_common_hf[i] / source_common_hf[i], 5)
                if source_common_hf[i] > 1e-7 else None for i in range(3)
            ],
            "causal_verdict": "UNASSESSED_NEEDS_UV_FOOTPRINT_AND_REAL_ANDROID_RASTER",
        }
    return {
        "comparison": measurements,
        "method": "Embedded glTF PBR-role RGB JPEGs, full-atlas Gaussian high-pass and Lanczos resolution controls. These are per-channel sRGB byte errors, NOT normal-vector angles, BRDF response, filtered screen pixels or visual acceptance.",
        "limitations": "Atlas pixel alignment, triangle UV area/visibility, alpha, color space and actual mobile texture mip/filtering are NOT established here. A detail loss does not prove Android image causality; no unrelated material source or original GLB is modified.",
    }



def _indexed_uv_surface_detail(path, max_samples_per_primitive=20000):
    """Measure high-pass texels *referenced* by rug triangles, not screen visibility.

    Source images are Lanczos-reduced to 1024px to match the packaged atlas.
    Indexed triangle centroids cover sampled UV positions; source/packaged
    topology and camera visibility can differ. Neither nearest UV lookup nor
    horizontal-normal stratification models Android filtering or occlusion.
    """
    raw, gltf, bo, bl = load(path)
    image_cache = {}
    detail_cache = {}
    material_roles = {}
    rows = []
    role_names = ("baseColor", "metallicRoughness", "normal")

    for mesh in gltf.get("meshes", []):
        for primitive in mesh.get("primitives", []):
            if primitive.get("mode", 4) != 4:
                raise ValueError("UV detail audit requires triangles")
            attrs = primitive["attributes"]
            material_index = primitive.get("material", 0)
            if material_index not in material_roles:
                roles = {}
                for role in role_names:
                    m = gltf["materials"][material_index]
                    pbr = m.get("pbrMetallicRoughness", {})
                    reference = (pbr.get("baseColorTexture") if role == "baseColor"
                                 else pbr.get("metallicRoughnessTexture") if role == "metallicRoughness"
                                 else m.get("normalTexture"))
                    if reference and "KHR_texture_transform" in reference.get("extensions", {}):
                        raise ValueError("KHR_texture_transform needs explicit UV transform audit")
                    meta, img = describe_texture(
                        gltf, raw, bo, bl, material_index, image_cache, role
                    )
                    if img is None:
                        roles[role] = None
                        continue
                    digest = (role, meta["embedded_image_sha256"])
                    if digest not in detail_cache:
                        common = img if img.size == (1024, 1024) else img.resize(
                            (1024, 1024), Image.Resampling.LANCZOS
                        )
                        highpass = ImageChops.difference(
                            common, common.filter(ImageFilter.GaussianBlur(radius=1.0))
                        )
                        detail_cache[digest] = highpass
                    roles[role] = (meta, detail_cache[digest])
                material_roles[material_index] = roles

            pc, _, position = accessor(gltf, raw, bo, bl, attrs["POSITION"])
            uv_readers = {}
            for role, info in material_roles[material_index].items():
                if info is None:
                    continue
                uv_name = "TEXCOORD_" + str(info[0]["uv_set"])
                if uv_name not in attrs:
                    raise ValueError("UV detail audit missing " + uv_name)
                uc, _, read_uv = accessor(gltf, raw, bo, bl, attrs[uv_name])
                if uc < pc:
                    raise ValueError("UV detail reader shorter than POSITION")
                uv_readers[role] = read_uv

            indexed = accessor(gltf, raw, bo, bl, primitive["indices"]) if "indices" in primitive else None
            icount = indexed[0] if indexed else pc
            if icount % 3:
                raise ValueError("Non-triangle indexed rug")
            triangles = icount // 3
            stride = max(1, math.ceil(triangles / max_samples_per_primitive))
            for ti in range(0, triangles, stride):
                indices = [int(indexed[2](ti * 3 + k)[0]) if indexed else ti * 3 + k
                           for k in range(3)]
                ps = [position(k) for k in indices]
                cx = sum(v[0] for v in ps) / 3
                cz = sum(v[2] for v in ps) / 3
                if not (math.isfinite(cx) and math.isfinite(cz)):
                    raise ValueError("Nonfinite rug centroid")
                e1 = [ps[1][k] - ps[0][k] for k in range(3)]
                e2 = [ps[2][k] - ps[0][k] for k in range(3)]
                n = (e1[1]*e2[2]-e1[2]*e2[1],
                     e1[2]*e2[0]-e1[0]*e2[2],
                     e1[0]*e2[1]-e1[1]*e2[0])
                magnitude = math.sqrt(sum(x*x for x in n))
                plane_alignment = abs(n[1]) / magnitude if magnitude > 1e-12 else None
                texcoords = {
                    role: tuple(sum(reader(k)[axis] for k in indices) / 3 for axis in range(2))
                    for role, reader in uv_readers.items()
                }
                rows.append((cx, cz, material_index, plane_alignment, texcoords))
    if not rows:
        raise ValueError("No rug triangles available for UV detail audit")
    lo_x, hi_x = min(r[0] for r in rows), max(r[0] for r in rows)
    lo_z, hi_z = min(r[1] for r in rows), max(r[1] for r in rows)
    if not (hi_x > lo_x and hi_z > lo_z):
        raise ValueError("Degenerate sampled rug spatial domain")

    def new_cell():
        return {"count": 0, "sum": [0., 0., 0.], "plane_aligned_count": 0,
                "plane_aligned_sum": [0., 0., 0.]}

    grouped = {role: defaultdict(new_cell) for role in role_names}
    unique_texels = {role: set() for role in role_names}
    for cx, cz, mid, plane_alignment, texcoords in rows:
        xx = min(3, max(0, int(4*(cx-lo_x)/(hi_x-lo_x))))
        zz = min(3, max(0, int(4*(cz-lo_z)/(hi_z-lo_z))))
        cell_key = f"x{xx}_z{zz}"
        for role, uv in texcoords.items():
            meta, highpass = material_roles[mid][role]
            u, v = uv
            s = meta["sampler"]
            px = min(highpass.width - 1, int(wrap_uv(u, s["wrapS"])*highpass.width))
            py = min(highpass.height - 1, int(wrap_uv(v, s["wrapT"])*highpass.height))
            rgb = highpass.getpixel((px, py))
            cell = grouped[role][cell_key]
            cell["count"] += 1
            unique_texels[role].add((mid, px, py))
            for k in range(3):
                cell["sum"][k] += rgb[k]
            if plane_alignment is not None and plane_alignment >= 0.5:
                cell["plane_aligned_count"] += 1
                for k in range(3):
                    cell["plane_aligned_sum"][k] += rgb[k]

    result = {}
    for role in role_names:
        cells = {}
        total_sum = [0., 0., 0.]
        total_n = 0
        for key, data in sorted(grouped[role].items()):
            n = data["count"]
            total_n += n
            total_sum = [total_sum[k] + data["sum"][k] for k in range(3)]
            nc = data["plane_aligned_count"]
            cells[key] = {
                "sampled_indexed_uv_centroids": n,
                "highpass_mae_rgb": [round(v / n, 5) for v in data["sum"]],
                "approximately_horizontal_normal_count": nc,
                "approximately_horizontal_normal_highpass_mae_rgb":
                    [round(v / nc, 5) for v in data["plane_aligned_sum"]] if nc else None,
            }
        if not total_n:
            raise ValueError("Missing PBR role UV references: " + role)
        result[role] = {
            "sample_count": total_n,
            "unique_sampled_atlas_texels_not_full_triangle_coverage": len(unique_texels[role]),
            "uv_centroid_highpass_mae_rgb": [round(v/total_n, 5) for v in total_sum],
            "normalized_local_xz_cells": cells,
        }
    return {
        "glb_sha256": hashlib.sha256(raw).hexdigest(),
        "sampling": "indexed triangle centroids, independent normalized 4x4 local XZ bins",
        "common_texture_resolution": [1024, 1024],
        "sampled_triangles_all_primitives": len(rows),
        "roles": result,
    }


def _uv_footprint_detail_comparison(source_path, packaged_path):
    source = _indexed_uv_surface_detail(source_path)
    packaged = _indexed_uv_surface_detail(packaged_path)
    comparison = {}
    for role in ("baseColor", "metallicRoughness", "normal"):
        a = source["roles"][role]
        b = packaged["roles"][role]
        ratios = []
        for k in range(3):
            aa, bb = a["uv_centroid_highpass_mae_rgb"][k], b["uv_centroid_highpass_mae_rgb"][k]
            ratios.append(round(bb/aa, 5) if aa > 1e-8 else None)
        spatial = {}
        for key in sorted(set(a["normalized_local_xz_cells"]) & set(b["normalized_local_xz_cells"])):
            u = a["normalized_local_xz_cells"][key]
            v = b["normalized_local_xz_cells"][key]
            if min(u["sampled_indexed_uv_centroids"], v["sampled_indexed_uv_centroids"]) < 20:
                continue
            spatial[key] = {
                "original_uv_highpass_mae_rgb": u["highpass_mae_rgb"],
                "packaged_uv_highpass_mae_rgb": v["highpass_mae_rgb"],
                "source_n": u["sampled_indexed_uv_centroids"],
                "packaged_n": v["sampled_indexed_uv_centroids"],
            }
        comparison[role] = {
            "packaged_over_original_uv_centroid_highpass_ratio_rgb": ratios,
            "paired_spatial_cells_at_least_20_samples": spatial,
        }
    return {
        "source": source,
        "packaged": packaged,
        "comparison": comparison,
        "diagnostic_limit": "UV-centroid-referenced atlas texels are NOT proven visible on Android. "
            "Independent source/derived triangles and atlas UV layouts are not pixel-registered; "
            "no occlusion, texture-coordinate derivatives, mip LOD, BRDF, GPU normal decode, "
            "direct side-by-side Android material substitution or causal visual PASS is asserted.",
    }



def _normal_direction(rgb, normal_scale):
    """Linear glTF tangent normal encoded in RGB, applying normalTexture.scale."""
    x = (2.0 * rgb[0] / 255.0 - 1.0) * normal_scale
    y = (2.0 * rgb[1] / 255.0 - 1.0) * normal_scale
    z = 2.0 * rgb[2] / 255.0 - 1.0
    n = math.sqrt(x*x + y*y + z*z)
    if n < 1e-8:
        raise ValueError("Degenerate encoded tangent normal")
    return x/n, y/n, z/n


def _normal_neighbor_angle_deg(center, neighbor, normal_scale):
    a = _normal_direction(center, normal_scale)
    b = _normal_direction(neighbor, normal_scale)
    dot = max(-1.0, min(1.0, sum(a[i]*b[i] for i in range(3))))
    return math.degrees(math.acos(dot))


def _indexed_normal_mip_sensitivity(path, max_samples_per_primitive=12000):
    """Measure actual indexed normal-map UV texel angular steps at hypothetical mips.

    No Android camera/raster, visibility, GPU mipmap generation, BRDF, or
    actual screen-space LOD is inferred; source/derived UVs differ.
    """
    raw, gltf, bo, bl = load(path)
    cache, atlas_cache, angle_samples = {}, {}, defaultdict(list)
    samplers = set()
    sampled_count = 0
    for mesh in gltf.get("meshes", []):
        for primitive in mesh.get("primitives", []):
            if primitive.get("mode", 4) != 4 or "POSITION" not in primitive.get("attributes", {}):
                raise ValueError("Normal mip audit requires triangular POSITION")
            material_index = primitive.get("material", 0)
            material = gltf["materials"][material_index]
            normal_info = material.get("normalTexture")
            if not normal_info:
                raise ValueError("Missing rug normal map; cannot compare tangent normal response")
            if "KHR_texture_transform" in normal_info.get("extensions", {}):
                raise ValueError("Texture transform requires explicit projection handling")
            normal_scale = float(normal_info.get("scale", 1.0))
            if not math.isfinite(normal_scale) or normal_scale < 0:
                raise ValueError("Invalid glTF normalTexture.scale")
            meta, rgb = describe_texture(gltf, raw, bo, bl, material_index, cache, "normal")
            if rgb is None:
                raise ValueError("Missing embedded normal image")
            image_key = meta["embedded_image_sha256"]
            if image_key not in atlas_cache:
                common = rgb if rgb.size == (1024, 1024) else rgb.resize(
                    (1024, 1024), Image.Resampling.LANCZOS
                )
                atlas_cache[image_key] = [
                    common.resize((1024 >> level, 1024 >> level), Image.Resampling.LANCZOS)
                    if level else common for level in range(4)
                ]
            sampler = meta["sampler"]
            samplers.add((sampler["wrapS"], sampler["wrapT"],
                          sampler["magFilter"], sampler["minFilter"], normal_scale))
            attrs = primitive["attributes"]
            uv_key = "TEXCOORD_" + str(meta["uv_set"])
            if uv_key not in attrs:
                raise ValueError("Normal map UV accessor missing: " + uv_key)
            vertex_count, _, _ = accessor(gltf, raw, bo, bl, attrs["POSITION"])
            uv_count, _, uv_reader = accessor(gltf, raw, bo, bl, attrs[uv_key])
            if uv_count < vertex_count:
                raise ValueError("UV count smaller than vertex count")
            indexed = accessor(gltf, raw, bo, bl, primitive["indices"]) if "indices" in primitive else None
            count = indexed[0] if indexed else vertex_count
            if count % 3:
                raise ValueError("Non-triangle normal mip indices")
            stride = max(1, math.ceil(count / 3 / max_samples_per_primitive))
            for ti in range(0, count // 3, stride):
                ids = [int(indexed[2](ti*3+k)[0]) if indexed else ti*3+k for k in range(3)]
                u = sum(uv_reader(k)[0] for k in ids) / 3.0
                v = sum(uv_reader(k)[1] for k in ids) / 3.0
                for level, img in enumerate(atlas_cache[image_key]):
                    x = min(img.width - 1, int(wrap_uv(u, sampler["wrapS"]) * img.width))
                    y = min(img.height - 1, int(wrap_uv(v, sampler["wrapT"]) * img.height))
                    xx = min(img.width - 1, int(wrap_uv(
                        u + 1.0/img.width, sampler["wrapS"]) * img.width))
                    yy = min(img.height - 1, int(wrap_uv(
                        v + 1.0/img.height, sampler["wrapT"]) * img.height))
                    center = img.getpixel((x, y))
                    delta = 0.5 * (
                        _normal_neighbor_angle_deg(center, img.getpixel((xx, y)), normal_scale)
                        + _normal_neighbor_angle_deg(center, img.getpixel((x, yy)), normal_scale)
                    )
                    angle_samples[level].append(delta)
                sampled_count += 1
    if not sampled_count:
        raise ValueError("No normal-map UV triangle samples")
    levels = {}
    for level in range(4):
        measured = sorted(angle_samples[level])
        if len(measured) != sampled_count:
            raise ValueError("Incomplete mip sample census")
        levels[str(level)] = {
            "hypothetical_atlas_px": 1024 >> level,
            "sampled_triangle_uv_centroids": len(measured),
            "mean_adjacent_texel_tangent_normal_angle_deg": round(sum(measured)/len(measured), 6),
            "p95_adjacent_texel_tangent_normal_angle_deg":
                round(measured[min(len(measured)-1, math.ceil(.95*len(measured))-1)], 6),
            "max_adjacent_texel_tangent_normal_angle_deg": round(measured[-1], 6),
        }
    return {
        "glb_sha256": hashlib.sha256(raw).hexdigest(),
        "hypothetical_mip_levels": levels,
        "sampler_metadata_wrap_filters_and_normal_scale":
            sorted(str(item) for item in samplers),
        "interpretation": "Normal-map RGB decoded to normalized glTF tangent vectors at indexed "
            "UV centroids. Mips are Pillow Lanczos hypotheses, not native Android GPU mips. "
            "No CALL-visible texel/LOD/occlusion/BRDF/lighting verified; no raster A/B or visual PASS.",
    }


def _normal_mip_angular_comparison(source_path, packaged_path):
    source = _indexed_normal_mip_sensitivity(source_path)
    packaged = _indexed_normal_mip_sensitivity(packaged_path)
    ratios = {}
    for level, s in source["hypothetical_mip_levels"].items():
        p = packaged["hypothetical_mip_levels"][level]
        ss = s["mean_adjacent_texel_tangent_normal_angle_deg"]
        pp = p["mean_adjacent_texel_tangent_normal_angle_deg"]
        ratios[level] = round(pp/ss, 6) if ss > 1e-9 else None
    return {
        "source": source,
        "packaged": packaged,
        "packaged_over_source_hypothetical_mip_mean_angle_ratios": ratios,
        "causal_status": "UNASSESSED_NO_ANDROID_SCREEN_SPACE_MIP_OR_FIXED_LIGHT_RASTER_AB",
        "diagnostic_limit": "Separate UV-centroid populations and hypothetical Lanczos mip "
            "response cannot establish corresponding camera-visible pixels or a product fix.",
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", type=Path, required=True)
    ap.add_argument("--derived", type=Path, required=True)
    ap.add_argument("--output", type=Path, required=True)
    args = ap.parse_args()
    original = profile(args.source)
    if original["glb_sha256"] != SOURCE_SHA or original["glb_bytes"] != SOURCE_SIZE:
        raise ValueError("Canonical original Teppisch.glb SHA256/size mismatch")
    packaged = profile(args.derived)
    other_pbr_roles = {}
    for role in ("metallicRoughness", "normal"):
        src_role = profile(args.source, role)
        derived_role = profile(args.derived, role)
        other_pbr_roles[role] = {
            "source_materials": src_role["materials"],
            "packaged_materials": derived_role["materials"],
            "source_sampled_indexed_triangles": src_role["sampled_indexed_triangles"],
            "packaged_sampled_indexed_triangles": derived_role["sampled_indexed_triangles"],
            "sampled_spatial_channel_delta": compare(src_role, derived_role),
        }
    report = {
        "schema":"celine-room-rug-atlas-indexed-uv-spatial/v1",
        "source":original,"packaged":packaged,
        "sampled_spatial_color_delta":compare(original,packaged),
        "sampled_metallic_roughness_normal_channel_deltas": other_pbr_roles,
        "pbr_full_atlas_high_frequency_diagnostic": _pbr_atlas_detail_audit(args.source, args.derived),
        "pbr_uv_referenced_high_frequency_diagnostic": _uv_footprint_detail_comparison(args.source, args.derived),
        "pbr_normal_map_hypothetical_mip_angular_diagnostic": _normal_mip_angular_comparison(args.source, args.derived),
        "structural_audit":"PASS",
        "android_home_call_home_visual_proof":"NOT_RUN",
        "whole_room_visual_acceptance":"FAIL_PREVIOUS_MANUAL_PROOF",
        "immutable_sources_mutated":False,
        "runtime_assets_mutated":False,
    }
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    print(f"CELINE_ROOM_RUG_ATLAS_UV_AUDIT PASS source_samples={original['sampled_indexed_triangles']} packaged_samples={packaged['sampled_indexed_triangles']} visualAcceptance=NOT_TESTED")


if __name__ == "__main__":
    main()
