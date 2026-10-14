# Hilbert Curve & Space-Filling Curves — Turning (lat, lng) into One Number

> **Goal of this guide:** understand, step by step, how a 2-D position like `(17.4435, 78.3772)` can be converted into a **single integer** such that places that are close on the map get close numbers — and how that single integer lets an ordinary B-tree index answer **range queries** ("everything within 2 km of me").
>
> Companion documents: [`LOCATION_DB.html`](LOCATION_DB.html) (GeoHash, grid, KD-Tree), [`GEOHASH_MAP_RENDERING.html`](GEOHASH_MAP_RENDERING.html) (drawing cells on a map). An interactive version of *this* guide, with live demos, is in [`HILBERT_CURVE.html`](HILBERT_CURVE.html).
>
> Every algorithm and every number below was produced by running the code shown in this document (round-trip tests, adjacency tests, and a 300-query benchmark) — nothing is copied from memory.

---

## Table of Contents

1. [The problem: two numbers, one index](#1-the-problem-two-numbers-one-index)
2. [A tiny practical example: numbering 16 houses](#2-a-tiny-practical-example-numbering-16-houses)
3. [What makes a "good" 2-D → 1-D mapping](#3-what-makes-a-good-2-d--1-d-mapping)
4. [A tour of the curves: Row-major, N, Z, U (Hilbert), and friends](#4-a-tour-of-the-curves)
5. [The Hilbert curve, step by step](#5-the-hilbert-curve-step-by-step)
6. [Implementing it in JavaScript](#6-implementing-it-in-javascript)
7. [From the whole world to a single line](#7-from-the-whole-world-to-a-single-line)
8. [Range queries with a Hilbert index](#8-range-queries-with-a-hilbert-index)
9. [Putting it into PureEats: schema, SQL, pipeline](#9-putting-it-into-pureeats)
10. [Where this is used in the real world](#10-where-this-is-used-in-the-real-world)
11. [Pitfalls & cheat sheet](#11-pitfalls--cheat-sheet)

---

## 1. The problem: two numbers, one index

A location is **two** numbers — latitude and longitude. A database index (B-tree, sorted set, LSM-tree, even a sorted file) is built for **one** key: it can only keep rows in *one* sorted order.

```
Index on ONE key:       ... 17 ─ 18 ─ 19 ─ 20 ─ 21 ...      ← easy: range = a contiguous slice
Location = TWO keys:    (17.44, 78.37)  ← which one do we sort by?
```

If you create a composite index `(lat, lng)` and ask for a small box around you:

```sql
WHERE lat BETWEEN 17.42 AND 17.46
  AND lng BETWEEN 78.35 AND 78.40
```

the index narrows `lat` nicely, but within that latitude band the rows are sorted by `lng` **across the entire planet** — so the database walks a thin horizontal strip that circles the whole Earth, and discards almost everything. The index only really helps with **one** of the two dimensions.

**The idea of a space-filling curve:** draw one continuous line that visits *every* cell of a 2-D grid exactly once. Number the cells in the order the line visits them. Now every cell has a **single integer** — its position along the line — and we can index that integer with a plain B-tree.

```
2-D map                              1-D line
┌───┬───┬───┬───┐
│   │   │   │   │                    0──1──2──3──4──5──6── … ──15
├───┼───┼───┼───┤        ───────►     (the curve, unrolled)
│   │   │   │   │
└───┴───┴───┴───┘
```

The whole game is: **choose a curve such that cells that are close in 2-D tend to be close along the line.** The Hilbert curve is the best-known answer.

---

## 2. A tiny practical example: numbering 16 houses

Imagine a small colony of **4 × 4 = 16 houses**. A postman wants a single house number for each.

### 2.1 The obvious way — row by row (row-major)

```
 0  1  2  3
 4  5  6  7
 8  9 10 11
12 13 14 15
```

Horizontal neighbours are great: house 5's neighbours 4 and 6 differ by 1. But **vertical** neighbours differ by 4: house 5 and the house directly below it, 9, are *four* apart. In a colony with 1,000,000 houses per row they would be **a million** apart. The numbering respects one direction and ignores the other.

### 2.2 A smarter way — follow a "U" shape recursively (Hilbert)

```
 0── 1  14──15
     │   │
 3── 2  13──12
 │           │
 4   7── 8  11
 │   │   │   │
 5── 6   9──10
```

The same 16 houses as a grid of numbers:

```
 0  1 14 15
 3  2 13 12
 4  7  8 11
 5  6  9 10
```

Look at what happened:

* Consecutive numbers are **always next-door neighbours** (0→1→2→3→4→… each step moves one house left/right/up/down). The curve never jumps.
* Vertical *and* horizontal neighbours tend to have close numbers. House `2`'s four neighbours are `1`, `3`, `7`, `13`: three of the four are within 5.
* Whole **blocks** get contiguous numbers: the top-left 2×2 block is `0–3`, the bottom-left block is `4–7`, the bottom-right is `8–11`, the top-right is `12–15`. Every block, at every size, is a contiguous run of numbers. **That is the property that makes range queries cheap.**

### 2.3 A first range query

"Find every house in the 2 × 2 block at columns 1–2, rows 1–2" (the four centre houses).

| Numbering | Numbers of the four houses | Contiguous runs ("ranges") |
|---|---|---|
| Row-major | 5, 6, 9, 10 | `[5–6]`, `[9–10]` → **2 ranges** |
| Z-order (§4.3) | 3, 6, 9, 12 | `[3]`, `[6]`, `[9]`, `[12]` → **4 ranges** |
| Hilbert | 2, 7, 8, 13 | `[2]`, `[7–8]`, `[13]` → **3 ranges** |

> **Honest note.** In this toy, row-major is just as good — or better. That is because each row has only 4 houses. Scale the colony up to a whole planet (millions of cells per row) and the picture reverses completely; §8.4 measures it. The point of a space-filling curve is *not* "fewest ranges in a toy"; it is **locality in both directions at every scale**.

---

## 3. What makes a good 2-D → 1-D mapping

| # | Property | Why we want it |
|---|---|---|
| 1 | **Bijective** — every cell ↔ exactly one number, and back | no collisions, and we can decode a number into a place |
| 2 | **Locality** — close in 1-D ⇒ close in 2-D | scanning a few consecutive keys reads a compact area |
| 3 | **Hierarchical / prefix property** — a big square = a contiguous run, and its 4 children are 4 consecutive sub-runs | one range covers a whole block; precision can be changed by dropping digits |
| 4 | **Cheap to compute** — `O(order)` bit operations | millions of points per second |
| 5 | **Continuity** (bonus) — consecutive numbers are adjacent cells | fewer "jumps", tighter ranges |

No mapping can be perfect: a plane has two directions and a line has one, so some neighbours *must* end up far apart (see §5.7). Different curves trade these properties differently.

---

## 4. A tour of the curves

All curves below work on a grid of `n × n` cells, `n = 2^order`. Cell `(x, y)` has `x` = column (left→right) and `y` = row (top→bottom). In every picture the curve starts at the **top-left** cell.

### 4.1 Row-major (scan-line)

`d = y · n + x`

```
 0  1  2  3
 4  5  6  7
 8  9 10 11
12 13 14 15
```

Trivial and cheap, but locality exists in one direction only. Not hierarchical: a square block is *not* a contiguous run.

### 4.2 N-order

The same as Z-order below but with the two axes swapped — it traces the letter **N** (down, diagonal back up, down). Rarely used on its own; it appears when the roles of `x` and `y` in the bit interleave are exchanged.

```
 0  2  8 10
 1  3  9 11
 4  6 12 14
 5  7 13 15
```

### 4.3 Z-order (Morton / Lebesgue curve)

Interleave the bits of `x` and `y`. Visiting order traces the letter **Z** at every scale: right, diagonal down-left, right.

```
 0  1  4  5
 2  3  6  7
 8  9 12 13
10 11 14 15
```

**Worked example** — encode cell `x = 5`, `y = 3` on an 8 × 8 grid (`order = 3`):

```
x = 5 = 1 0 1   (binary, MSB → LSB)
y = 3 = 0 1 1

interleave, taking  y-bit first then x-bit  at each level:

   level:    2     1     0
   y bits:   0     1     1
   x bits:   1     0     1
             ↓     ↓     ↓
   pairs:   0 1   1 0   1 1      →   0 1 1 0 1 1   (base 2)  =  27
```

So cell `(5, 3)` → `d = 27`. Decoding is the reverse: un-interleave the bits.

* **Hierarchical:** ✔ — each pair of bits is one quadrant choice, so a 2^k × 2^k block is a contiguous run.
* **Continuous:** ✘ — look at `7 → 8` in the grid above: cell `(3,1)` jumps to cell `(0,2)`, across the whole grid. These **jumps** are Z-order's weakness: a window straddling a jump needs extra ranges.
* **Very cheap:** just bit shuffling (there are branch-free "magic number" tricks).
* **GeoHash is Z-order.** GeoHash alternates longitude and latitude bits exactly like this, then writes 5 bits at a time in Base-32. If you read [`LOCATION_DB.html`](LOCATION_DB.html) Phase 4, you have already used a Z-curve.

### 4.4 U-order — the Hilbert curve

The base pattern is a **U** (open at the top for our top-left start). Each quadrant is filled with a smaller U, rotated or mirrored so that the end of one quadrant touches the start of the next. Result: **no jumps — ever.** This is the subject of §5.

### 4.5 Other members of the family

| Curve | Base shape | Notes |
|---|---|---|
| **Peano** | 3 × 3 serpentine ("S"/"N" snake) | First space-filling curve (1890). Works on 3^k grids, not powers of two. |
| **Moore** | four Hilbert curves arranged in a ring | A *closed loop* variant of Hilbert: end meets start. Useful for periodic domains. |
| **Gray-code curve** | Z-like, but the interleaved bits are Gray-coded | Reported to cluster somewhat better than Z; slightly more expensive. |
| **Sierpiński** | triangles | Used on triangulated meshes. |
| **βΩ (beta-omega)** | Hilbert-like (Wierum) | Less common; listed because it is the best-known Greek-letter-named curve. |

> **About "alpha curve":** I am not aware of a standard, widely-documented space-filling curve called an "alpha curve" — the common letter-named ones are **Z**, **N**, **U** (Hilbert), and the Greek-letter **βΩ** curve. If your source used "alpha curve" for something specific, check its definition; it may be a local or informal name for one of the above. Everything in this guide (locality, prefix property, range decomposition) applies unchanged to any *hierarchical* curve.

### 4.6 Side-by-side

| Property | Row-major | N / Z-order | Hilbert (U) | Moore |
|---|---|---|---|---|
| Hierarchical (blocks = ranges) | ✘ | ✔ | ✔ | ✔ |
| Continuous (no jumps) | ✘ (jump at each row end) | ✘ | ✔ | ✔ (and closed) |
| Cost to compute | trivial | very cheap | cheap (`O(order)` loop) | cheap |
| Range-query tightness | poor at scale | good | **best of the group** | like Hilbert |
| Used by | flat arrays | GeoHash, Z-order clustering | Google S2, Hilbert R-trees | periodic domains |

---

## 5. The Hilbert curve, step by step

### 5.1 Level 1 — a single "U"

Split the square into four quadrants and visit them in a **U** order:

```
0  3
│  │
1──2
```

Quadrant numbers: `0` = top-left, `1` = bottom-left, `2` = bottom-right, `3` = top-right.

### 5.2 Level 2 — shrink the U into every quadrant

Put a smaller U into each quadrant, but **rotate/mirror** them so the curve stays connected:

* quadrant 0 (top-left) — the U is **transposed** (flipped over the main diagonal), so that it *ends* at the bottom-left, where quadrant 1 begins;
* quadrants 1 and 2 (bottom row) — same orientation as the parent U;
* quadrant 3 (top-right) — **anti-transposed** (flipped over the other diagonal), so that it *starts* at the bottom-right, right above where quadrant 2 ends.

```
 0── 1  14──15
     │   │
 3── 2  13──12
 │           │
 4   7── 8  11
 │   │   │   │
 5── 6   9──10
```

### 5.3 Level 3 — repeat

```
 0   3── 4── 5  58──59──60  63
 │   │       │   │       │   │
 1── 2   7── 6  57──56  61──62
         │           │
14──13   8── 9  54──55  50──49
 │   │       │   │       │   │
15  12──11──10  53──52──51  48
 │                           │
16──17  30──31──32──33  46──47
     │   │           │   │
19──18  29──28  35──34  45──44
 │           │   │           │
20  23──24  27  36  39──40  43
 │   │   │   │   │   │   │   │
21──22  25──26  37──38  41──42
```

The same curve as numbers (8 × 8):

```
 0  3  4  5 58 59 60 63
 1  2  7  6 57 56 61 62
14 13  8  9 54 55 50 49
15 12 11 10 53 52 51 48
16 17 30 31 32 33 46 47
19 18 29 28 35 34 45 44
20 23 24 27 36 39 40 43
21 22 25 26 37 38 41 42
```

Check the block property: the top-left 4 × 4 block holds `0–15`; the next block (bottom-left) holds `16–31`; bottom-right `32–47`; top-right `48–63`. And inside `0–15`, the top-left 2 × 2 holds `0–3`. **Every aligned square is a contiguous run.**

### 5.4 The recursive rule, as a table

Every level repeats the same step: *pick the quadrant, add its offset, then transform the coordinates inside it so the sub-problem looks like the original.*

| Quadrant | Position | Hilbert digit | Offset added at this level | Transform applied to `(x, y)` for the next level |
|---|---|---|---|---|
| top-left | `rx=0, ry=0` | 0 | `0 · s²` | **swap** `x ↔ y` |
| bottom-left | `rx=0, ry=1` | 1 | `1 · s²` | none |
| bottom-right | `rx=1, ry=1` | 2 | `2 · s²` | none |
| top-right | `rx=1, ry=0` | 3 | `3 · s²` | **flip** both (`x → n-1-x`, `y → n-1-y`) **then swap** |

(`s` is the half-size at the current level; `rx`, `ry` are the bits of `x`, `y` at that level. Note the digit is `(3·rx) XOR ry`.)

### 5.5 Worked example — encode `(x=6, y=5)` on the 8 × 8 grid

`n = 8`, so we look at bits `s = 4`, then `2`, then `1`.

| Step | `s` | `(x, y)` now | `rx` | `ry` | digit `(3·rx)^ry` | add `s²·digit` | running `d` | transform → next `(x, y)` |
|---|---|---|---|---|---|---|---|---|
| 1 | 4 | (6, 5) | 1 | 1 | 2 | 16 · 2 = 32 | **32** | bottom-right: none → (6, 5) |
| 2 | 2 | (6, 5) | 1 | 0 | 3 | 4 · 3 = 12 | **44** | top-right: flip → (1, 2), swap → **(2, 1)** |
| 3 | 1 | (2, 1) | 0 | 1 | 1 | 1 · 1 = 1 | **45** | bottom-left: none |

Result: **`(6, 5) → 45`**. Look it up in the 8 × 8 grid above: row 5, column 6 → `45`. ✔

In base 4 the answer is `2 3 1` — **one base-4 digit per level**: first "bottom-right", then "top-right", then "bottom-left". That is exactly the **path down a quadtree**, which is the next idea.

### 5.6 The prefix (quadtree) property

Because each level contributes one base-4 digit, the index of a cell at order `k+1` is its parent's index at order `k` times four, plus one digit:

```
d(k+1) = 4 · d(k) + digit         ⇔        d(k) = ⌊ d(k+1) / 4 ⌋
```

Example, Hyderabad `(17.4435, 78.3772)` at increasing orders (computed with the code in §6):

| Order | Grid | Hilbert index | in base 4 | Observation |
|---|---|---|---|---|
| 1 | 2 × 2 | `3` | `3` | |
| 2 | 4 × 4 | `13` | `3 1` | `13 = 3·4 + 1` |
| 3 | 8 × 8 | `52` | `3 1 0` | `52 = 13·4 + 0` |
| 4 | 16 × 16 | `209` | `3 1 0 1` | `209 = 52·4 + 1` |

So "all places inside the order-2 cell `13`" is simply "all indexes starting with the digits `31`" — i.e. the range `[13·4^(k−2), 14·4^(k−2) − 1]` at any finer order `k`. **Dropping digits = zooming out; adding digits = zooming in.** (This is the same prefix trick GeoHash plays with strings.)

### 5.7 Why it works — and where it cannot

* **Continuity:** consecutive indexes are always adjacent cells (verified by script for orders up to 8). Walking along the line never jumps.
* **Locality, one direction:** two cells that are close *along the line* are guaranteed close in the plane (they are within a small block).
* **Locality, the other direction is NOT guaranteed.** Two cells that are neighbours in the plane can be very far apart on the line. The worst case is a **seam** between the big quadrants: in the 4 × 4 grid, cell `1` (top row, column 1) and cell `14` (top row, column 2) touch, but their indexes differ by 13. No 2-D → 1-D mapping can avoid this. Consequence for queries: a window that straddles a seam turns into **several ranges** (§8) — you must always query ranges, never "one interval".

---

## 6. Implementing it in JavaScript

### 6.1 Index ⇄ cell (works up to `order = 26` with plain Numbers)

`n = 2^order`. Coordinates `(x, y)` are integers in `[0, n)`. The index `d` is in `[0, n²)`. JavaScript Numbers hold integers exactly up to 2^53, so `2·order ≤ 53` ⇒ **order ≤ 26** (cells ≈ 0.6 m at the equator — plenty).

```js
// Flip/swap the sub-square so the next level looks like the original problem.
function rot(n, x, y, rx, ry) {
  if (ry === 0) {
    if (rx === 1) { x = n - 1 - x; y = n - 1 - y; }   // flip (top-right quadrant)
    [x, y] = [y, x];                                   // swap (top-left AND top-right)
  }
  return [x, y];
}

// (x, y) -> d     order: 1..26
function hilbertXY2D(order, x, y) {
  const n = 2 ** order;
  let d = 0;
  for (let s = n / 2; s >= 1; s /= 2) {
    const rx = (x & s) > 0 ? 1 : 0;        // which half in x at this level?
    const ry = (y & s) > 0 ? 1 : 0;        // which half in y at this level?
    d += s * s * ((3 * rx) ^ ry);          // digit 0,1,2,3 weighted by s²
    [x, y] = rot(n, x, y, rx, ry);         // re-orient for the next level
  }
  return d;
}

// d -> (x, y)
function hilbertD2XY(order, d) {
  const n = 2 ** order;
  let x = 0, y = 0, t = d;
  for (let s = 1; s < n; s *= 2) {
    const rx = Math.floor(t / 2) % 2;      // digits are consumed from the LAST one
    const ry = (t % 2) ^ rx;
    [x, y] = rot(s, x, y, rx, ry);
    x += s * rx;
    y += s * ry;
    t = Math.floor(t / 4);
  }
  return [x, y];
}
```

Quick checks:

```js
hilbertXY2D(3, 6, 5);        // 45   (the worked example in §5.5)
hilbertD2XY(3, 45);          // [6, 5]
hilbertXY2D(2, 1, 0);        // 1    (row 0, column 1 of the 4×4 grid)
```

### 6.2 BigInt version (needed beyond `order = 26`; up to 32 → a 64-bit key)

```js
function hilbertXY2DBig(order, x, y) {                 // x, y, result are BigInt
  const n = 1n << BigInt(order);
  let d = 0n;
  for (let s = n >> 1n; s > 0n; s >>= 1n) {
    const rx = (x & s) > 0n ? 1n : 0n;
    const ry = (y & s) > 0n ? 1n : 0n;
    d += s * s * ((3n * rx) ^ ry);
    if (ry === 0n) {
      if (rx === 1n) { x = n - 1n - x; y = n - 1n - y; }
      [x, y] = [y, x];
    }
  }
  return d;
}
```

Order 32 gives a 2^32 × 2^32 grid and indexes up to 2^64 − 1 (fits an *unsigned* 64-bit column; use order ≤ 31 for a signed `BIGINT`).

### 6.3 Z-order for comparison

```js
// x in the even bit positions, y in the odd ones. (order ≤ 26)
function mortonXY2D(order, x, y) {
  let d = 0;
  for (let i = 0; i < order; i++) {
    d += ((x >> i) & 1) * 4 ** i + ((y >> i) & 1) * 2 * 4 ** i;
  }
  return d;
}
```

### 6.4 How this was verified

* Round trip `d2xy(xy2d(x, y)) = (x, y)` for **every** cell at orders 1–5 and 8, and 2,000 random cells at order 24.
* **Adjacency:** for every `d`, cell(`d`) and cell(`d−1`) differ by exactly 1 in Manhattan distance (orders 1–5, 8) — i.e. the curve is continuous.
* The BigInt version equals the Number version on 3,000 random inputs (orders 1–24), and round-trips 3,000 random cells at order 32.
* Prefix property `⌊d_k / 4⌋ = d_(k−1)` holds on 2,000 random order-20 cells.

---

## 7. From the whole world to a single line

### 7.1 Four steps: lat/lng → integer cell → index

```
(lat, lng)  ──►  unit square  ──►  integer cell (x, y)  ──►  Hilbert index d
 17.4435,        u = (lng+180)/360        x = ⌊u · 2^order⌋       d = hilbertXY2D(order, x, y)
 78.3772         v = (90−lat)/180         y = ⌊v · 2^order⌋
```

* We use the simple **equirectangular** mapping: longitude `−180…180` → `x`, latitude `90…−90` → `y` (north is row 0, so the first U sits "upright" on the map).
* The planet is a rectangle **2 : 1** (360° × 180°). We simply cut it into a `2^order × 2^order` grid — each cell is twice as wide (in degrees) as it is tall, i.e. cells are `360/2^k` ° × `180/2^k` °.

```js
function latLngToHilbert(order, lat, lng) {
  const n = 2 ** order;
  const x = Math.min(n - 1, Math.floor(((lng + 180) / 360) * n));   // clamp lng = 180
  const y = Math.min(n - 1, Math.floor(((90 - lat) / 180) * n));    // clamp lat = -90
  return hilbertXY2D(order, x, y);
}

function hilbertToBounds(order, d) {            // the rectangle a given index stands for
  const n = 2 ** order;
  const [x, y] = hilbertD2XY(order, d);
  return {
    minLng: (x / n) * 360 - 180,  maxLng: ((x + 1) / n) * 360 - 180,
    maxLat: 90 - (y / n) * 180,   minLat: 90 - ((y + 1) / n) * 180,
  };
}
```

### 7.2 Zooming in — each order is one more "level of U"

| Order | Grid | Cells | Index bits | Cell size (lng × lat) | ≈ at the equator |
|---|---|---|---|---|---|
| 1 | 2 × 2 | 4 | 2 | 180° × 90° | 20,037 km × 10,002 km |
| 2 | 4 × 4 | 16 | 4 | 90° × 45° | 10,019 km × 5,001 km |
| 3 | 8 × 8 | 64 | 6 | 45° × 22.5° | 5,009 km × 2,500 km |
| 4 | 16 × 16 | 256 | 8 | 22.5° × 11.25° | 2,505 km × 1,250 km |
| 8 | 256 × 256 | 65,536 | 16 | 1.406° × 0.703° | 156.5 km × 78.1 km |
| 12 | 4,096 × 4,096 | 16.8 M | 24 | 0.0879° × 0.0439° | 9.78 km × 4.88 km |
| 16 | 65,536 × 65,536 | 4.29 B | 32 | 0.00549° × 0.00275° | 611 m × 305 m |
| 20 | 1,048,576² | 1.10 T | 40 | 0.000343° × 0.000172° | 38.2 m × 19.1 m |
| 24 | 16,777,216² | 281 T | 48 | 0.0000215° × 0.0000107° | 2.4 m × 1.2 m |

Each extra order **quadruples** the number of cells and **halves** each side. The index of a point is simply `2 × order` bits long.

> Cell sizes in *kilometres* are only exact at the equator. The east–west width shrinks by `cos(latitude)` — at Hyderabad (17°) an order-20 cell is about 36 m wide, not 38 m.

### 7.3 The world at orders 1, 2 and 3

**Order 1 — four giant cells** (north is up, west is left):

```
   lng −180 ─────── 0 ─────── +180
lat  ┌──────────┬──────────┐
+90  │    0     │    3     │     0 = North America, Atlantic, UK/W. Europe (west of 0°)
     │          │          │     3 = Europe east of 0°, N. Africa, all of Asia (incl. India)
  0  ├──────────┼──────────┤
     │    1     │    2     │     1 = South America, S. Pacific
     │          │          │     2 = Southern Africa, Indian Ocean, Australia
−90  └──────────┴──────────┘
```

The curve goes **0 → 1 → 2 → 3**: down the west, across the south, up the east. One big "U" laid over the planet. (London, at 0.13° W, lands just inside quadrant 0; Hyderabad is in quadrant 3.)

**Order 2 — each quadrant is filled with a smaller, rotated U:**

```
 0── 1  14──15
     │   │
 3── 2  13──12
 │           │
 4   7── 8  11
 │   │   │   │
 5── 6   9──10
```

At order 2, Hyderabad, Chennai, Mumbai and Delhi are all in cell `13`; London is in `1`; New York in `2`; Sydney in `11`.

**Order 3 (the 8 × 8 picture of §5.3)** — Hyderabad, Chennai and Mumbai still share cell `52`, Delhi is in the neighbouring cell `55`; London is `6` and New York `8`, Sydney `44`.

Real indexes (from the code; city-centre coordinates Hyderabad 17.385, 78.4867 · Chennai 13.0827, 80.2707 · Mumbai 19.076, 72.8777 · Delhi 28.6139, 77.209 · London 51.5074, −0.1278 · New York 40.7128, −74.006 · Sydney −33.8688, 151.2093):

| City | Order 3 | Order 8 | Order 16 |
|---|---|---|---|
| Hyderabad | 52 | 53,632 | 3,514,884,372 |
| Chennai | 52 | 53,528 | 3,508,059,530 |
| Mumbai | 52 | 53,685 | 3,518,341,720 |
| Delhi | 55 | 56,875 | 3,727,379,662 |
| London | 6 | 6,761 | 443,128,598 |
| New York | 8 | 8,307 | 544,412,351 |
| Sydney | 44 | 45,903 | 3,008,358,009 |

At order 8 (65,536 cells) Hyderabad, Chennai and Mumbai are within ~160 of one another on the line, and Delhi is ~3,200 from Hyderabad. But notice the limit of the idea: Sydney (roughly 9,300 km from Hyderabad) is only ~7,700 away on the line, while London (roughly 7,500 km) is ~46,900 away. **Close on the line ⇒ close on the map; the reverse, and any exact ordering by distance, is not guaranteed** (§5.7).

### 7.4 A point down the levels

Hyderabad at order 20 → `d = 899,808,907,534`, which stands for the cell

```
lat 17.443371 … 17.443542      lng 78.377151 … 78.377495      (≈ 19 m × 36 m)
```

A restaurant ≈ 190 m east of it (`lng = 78.3790`) gets `d = 899,808,907,541` — a difference of **7** on a line with 1.1 trillion positions. At order 16 both points share the *same* cell (`3,514,878,545`), because that cell is 611 m wide.

### 7.5 Caveats of the equirectangular world

1. **Distortion.** Cells are the same *degrees* everywhere but shrink in *kilometres* toward the poles (the same issue the GeoHash docs describe).
2. **Antimeridian.** Longitude `+179.99°` and `−179.99°` are 2 km apart in reality but at opposite ends of the grid — a box crossing the 180° line must be split in two.
3. **Poles.** Row 0 and the last row collapse to points on a sphere but are full-width rows here.
4. **Production answer: Google S2** projects the sphere onto the six faces of a **cube**, then runs a Hilbert curve on each face, which keeps cells much closer to uniform. The idea you learned here is exactly what S2 does; only the projection differs.

---

## 8. Range queries with a Hilbert index

### 8.1 The query

> "Find restaurants within 2 km of `(17.4435, 78.3772)`."

Pipeline: **bounding box → cells → index ranges → B-tree range scans → exact distance filter.**

```
radius 2 km
   │  box: Δlat = r/111,  Δlng = r/(111·cos lat)        (LOCATION_DB.html, Phase 2)
   ▼
bbox (lat 17.4255…17.4615, lng 78.3583…78.3961)
   │  convert corners to integer cells at order 20
   ▼
cell rectangle  x: 752,523…752,633  (111 cells wide)   y: 422,567…422,777  (211 cells tall)
   │  decompose into Hilbert index ranges
   ▼
3 ranges   [a1,b1] [a2,b2] [a3,b3]
   │  3 index range scans  (WHERE hilbert_idx BETWEEN … OR …)
   ▼
~ candidates  →  Haversine filter  →  final answer
```

### 8.2 Why a rectangle becomes *several* ranges

On the 16 × 16 grid, the window `x = 5…10, y = 6…11` (a 6 × 6 block, 36 cells) occupies these index runs:

```
Hilbert (7 ranges): [40–45] [112–116] [119–120] [123–132] [135–136] [139–143] [210–215]
Z-order (12 ranges): [57] [59–63] [104–108] [110] [145] [147–151] [153] [155–159] [192–196] [198] [200–204] [206]
N-order  (8 ranges): [54–55] [60–63] [98–99] [102–111] [148–151] [156–157] [192–201] [204–205]
Row-major (6 ranges): [101–106] [117–122] [133–138] [149–154] [165–170] [181–186]
```

(All sets cover the same 36 cells; only the numbering differs.) Each range is a stretch of the line that is **entirely inside** the window — so scanning it returns only useful rows.

### 8.3 The decomposition algorithm (quadtree walk)

Recursively ask, for each square cell (starting with the whole world):

* **Outside** the query? → discard.
* **Completely inside**? → emit its whole index range `[prefix·4^m, (prefix+1)·4^m − 1]` — one range for an arbitrarily large area.
* **Partly overlapping**? → split into its 4 children (their index prefixes are `prefix·4 + 0..3`) and repeat.

Splitting forever would produce as many ranges as boundary cells, so we **stop early** when we have used our *range budget*. Cells that are still "partly overlapping" are then emitted whole — they may contain a few unwanted points, which the final exact-distance filter removes. This trades a few extra rows for a bounded number of index scans.

```js
// Cells of the quadtree are addressed by (level, prefix). A cell at `level` covers the
// index range [prefix·4^(order-level), (prefix+1)·4^(order-level) − 1]  and is an aligned
// square of side 2^(order-level).  Its position follows from decoding its first index.
function hilbertCover(order, qx0, qy0, qx1, qy1, maxRanges = 16) {   // inclusive cell rectangle
  const cell = (level, prefix) => {
    const span = 4 ** (order - level), size = 2 ** (order - level);
    const first = prefix * span;
    const [x, y] = hilbertD2XY(order, first);
    return { level, prefix, first, last: first + span - 1,
             cx: x - (x % size), cy: y - (y % size), size };
  };
  const classify = (c) => {
    if (c.cx > qx1 || c.cx + c.size - 1 < qx0 || c.cy > qy1 || c.cy + c.size - 1 < qy0) return 'out';
    if (c.cx >= qx0 && c.cx + c.size - 1 <= qx1 && c.cy >= qy0 && c.cy + c.size - 1 <= qy1) return 'in';
    return 'partial';
  };
  const merge = (cells) => {                       // join runs that touch: [a,b] + [b+1,c] → [a,c]
    const sorted = cells.map(c => [c.first, c.last]).sort((a, b) => a[0] - b[0]);
    const out = [];
    for (const r of sorted) {
      if (out.length && r[0] === out[out.length - 1][1] + 1) out[out.length - 1][1] = r[1];
      else out.push([r[0], r[1]]);
    }
    return out;
  };

  let full = [], partial = [cell(0, 0)];
  if (classify(partial[0]) === 'in') return { full: partial, partial: [], ranges: merge(partial) };

  for (let level = 0; level < order && partial.length; level++) {
    const nextFull = full.slice(), nextPartial = [];
    for (const p of partial) {
      for (let i = 0; i < 4; i++) {
        const child = cell(level + 1, p.prefix * 4 + i);
        const k = classify(child);
        if (k === 'in') nextFull.push(child);
        else if (k === 'partial') nextPartial.push(child);
      }
    }
    if (merge(nextFull.concat(nextPartial)).length > maxRanges) break;   // budget exhausted
    full = nextFull; partial = nextPartial;
  }
  // `full`    cells: every point inside is a certain hit.
  // `partial` cells: candidates — must be filtered with the exact test.
  return { full, partial, ranges: merge(full.concat(partial)) };
}
```

Properties checked by script on 200 random windows (order 6): with an unlimited budget the ranges equal the exact brute-force ranges; with a budget of 6 the result never has more than 6 ranges and **always covers every cell of the window** (it is a superset, never a miss).

### 8.4 Is it actually better? (measured, not assumed)

300 random square windows on a 256 × 256 grid (order 8). "Scan overhead" = cells that must be read ÷ cells actually needed, when we are only allowed `R` index ranges (the best possible merging of the exact ranges: always close the smallest gaps first). **1.00× would be perfect.**

**Window 20 × 20 cells**

| Curve | avg. exact ranges | R = 4 | R = 8 | R = 16 |
|---|---|---|---|---|
| Row-major | 20.0 | 10.44× | 8.08× | 3.36× |
| Z-order | 36.0 | 1.80× | 1.31× | 1.11× |
| **Hilbert** | **19.3** | **1.44×** | **1.13×** | **1.03×** |

**Window 8 × 8** — Hilbert: 1.28× / 1.04× / 1.00× (R = 4/8/16); Z: 1.57× / 1.18× / 1.03×; Row-major: 16.5× / 1.00× / 1.00×.
**Window 40 × 40** — Hilbert: 1.53× / 1.19× / 1.06×; Z: 1.88× / 1.40× / 1.16×; Row-major: 5.86× / 5.32× / 4.24×.

What this tells us, honestly:

1. **Exact range count:** Hilbert ≈ Row-major (≈ one range per window row), and Z is about **2× worse**. So "Hilbert needs the fewest ranges" is *not* the right claim.
2. **With a range budget** — which is the real situation, because each range is one index seek — the space-filling curves are dramatically better once the window has more rows than the budget: Hilbert reads **at most ~50 % extra** rows (1.00×–1.53× in every cell of the tables for R ≥ 4), while row-major reads **3×–16× more** than needed, because merging its row-ranges drags in whole world-wide rows. (Row-major is perfect only when the budget ≥ number of window rows, e.g. 8 rows with R ≥ 8.) On a real planet-sized grid the row-major gap grows with the grid width.
3. **Z-order reads ~3–25 % more rows than Hilbert** for the same budget (and needs ~2× the exact ranges) — the cost of Z's jumps.
4. A single range (`R = 1`, i.e. "scan from the smallest to the largest index") is bad for *every* curve (6×–43× overhead here; Hilbert is actually the worst of the three at `R = 1`) — plan for several ranges.
5. The simple level-by-level cover in §8.3 is less tight than the optimal merge (≈ 1.4× at budget 8 on these windows) but is simple, fast, and always correct. Better covers refine the *most wasteful* partial cell first.

**On the real example** (2 km radius around HiTech City, order 20 — a 111 × 211-cell window = 23,421 cells):

| Budget | Ranges produced | Cells scanned | Overhead |
|---|---|---|---|
| 4 or 8 | **3** | 28,672 | 1.22× |
| 16 | 10 | 26,880 | 1.15× |

```
[ 899808855040 , 899808857087 ]
[ 899808860160 , 899808870399 ]
[ 899808903168 , 899808919551 ]      ← contains the centre point  d = 899,808,907,534
```

Three index range scans cover the whole ≈ 4 km × 4 km box; only rows whose `hilbert_idx` falls in those ranges are read, and the Haversine step then trims the corners.

### 8.5 Radius queries and nearest-neighbour

* **Radius** → bounding box (above) → ranges → fetch → **Haversine** the candidates, keep `≤ r`. (The box is a *superset* of the circle; the corners are exactly the false positives the Haversine step removes. See `LOCATION_DB.html` Phase 2.)
* **Nearest-N** → start with a small radius; if fewer than N results come back, double the radius and repeat (re-using the same code). Scanning "a few keys left and right of my own index" is a handy first guess but is *not* guaranteed to find the true nearest, because of seams (§5.7).
* **Polygon** → decompose the polygon's bounding box, then run point-in-polygon on the candidates.

---

## 9. Putting it into PureEats

Using the Hilbert key next to (or instead of) the GeoHash prefix:

```sql
ALTER TABLE restaurant ADD COLUMN hilbert_idx BIGINT;          -- order 20 → fits easily in BIGINT
CREATE INDEX idx_restaurant_hilbert ON restaurant (hilbert_idx);
```

**Write path** (when a restaurant is created/moved): compute

```js
const hilbertIdx = latLngToHilbert(20, restaurant.lat, restaurant.lng);   // store it
```

**Read path** (nearby search):

```js
// radius → bounding box (LOCATION_DB.html Phase 2) → cell rectangle → ranges.
// Ignores the antimeridian and the poles (see §7.5).
function coverRadius(order, lat, lng, km, maxRanges) {
  const n = 2 ** order;
  const dLat = km / 111.0, dLng = km / (111.0 * Math.cos(lat * Math.PI / 180));
  const cx = (g) => Math.min(n - 1, Math.max(0, Math.floor(((g + 180) / 360) * n)));
  const cy = (t) => Math.min(n - 1, Math.max(0, Math.floor(((90 - t) / 180) * n)));
  return hilbertCover(order, cx(lng - dLng), cy(lat + dLat), cx(lng + dLng), cy(lat - dLat), maxRanges);
}

const { ranges } = coverRadius(20, lat, lng, 2 /* km */, 8 /* max ranges */);
// build:  WHERE (hilbert_idx BETWEEN ? AND ? OR hilbert_idx BETWEEN ? AND ? OR …)
// then:   keep rows with haversine(lat, lng, r.lat, r.lng) <= 2
```

```sql
SELECT id, name, lat, lng
FROM   restaurant
WHERE  (hilbert_idx BETWEEN 899808855040 AND 899808857087
     OR hilbert_idx BETWEEN 899808860160 AND 899808870399
     OR hilbert_idx BETWEEN 899808903168 AND 899808919551)
-- then in the application (or a SQL function): exact Haversine filter ≤ 2 km
```

Where it fits the pipeline in `LOCATION_DB.html`:

```
User request → bounding box → HILBERT RANGES (instead of / alongside GeoHash prefixes)
            → candidate rows → (KD-Tree / sort) → Haversine final ranking → results
```

Operational notes:

* The index is **immutable per location** — it only changes when the location changes. Moving drivers need an update per move (like any index).
* Pick the **order** from your typical query radius: choose the order whose cell is a few times smaller than the radius (order 16–20 for a 1–5 km radius). Too coarse ⇒ big overhead; too fine ⇒ more cells to describe (but the range count stays bounded by your budget).
* Store `order` with the data (or use one fixed order everywhere). Because of the prefix property you can still query at a coarser level by dividing: `hilbert_idx / 4^(20−k)`.
* If you need uniform cell size worldwide, switch to an S2-style projection (§7.5) rather than the equirectangular grid used here.

---

## 10. Where this is used in the real world

| System | Curve | What for |
|---|---|---|
| **Google S2** | Hilbert curve on each face of a cube | Cell IDs for geospatial indexing (Maps, many dispatch/ride systems) |
| **GeoHash** | Z-order (Base-32 text) | Prefix-searchable location keys (the `LOCATION_DB.html` guide) |
| **Hilbert-packed R-trees** (e.g. FlatGeobuf) | Hilbert | Sort features by Hilbert index so nearby shapes share R-tree nodes → compact, fast files |
| **Data-warehouse clustering** (`ZORDER BY` in Delta Lake, and similar) | Z-order (Hilbert in some systems) | Co-locate rows with similar values in several columns so queries skip files |
| **Image/texture & memory layout** | Z-order / Hilbert | Cache-friendly 2-D array traversal |

(Uber **H3** is *not* a space-filling-curve index: it uses hexagons with its own hierarchical numbering.)

---

## 11. Pitfalls & cheat sheet

**Pitfalls**

1. **One interval ≠ a rectangle.** A window is always several ranges; budget them.
2. **Seams.** Neighbours in space can be far apart in index; never assume `|d₁ − d₂|` small ⇔ close (only the reverse holds).
3. **Antimeridian & poles** need special handling with the equirectangular grid (split the box; clamp latitude).
4. **Number precision.** Plain JS Numbers are exact only to 2^53 ⇒ `order ≤ 26`. Use `BigInt` (and a 64-bit column) beyond that.
5. **Bitwise operators in JS are 32-bit.** `x & s` silently breaks for `x ≥ 2^31`; the BigInt version avoids it.
6. **Always post-filter** with the exact predicate (Haversine / point-in-polygon). The index gives *candidates*.
7. **Fix the convention once** (north = row 0, `x` = lng, `y` = lat, order, clamping) and use it for both writing and querying.

**Cheat sheet**

```
index(lat,lng, order)  = hilbertXY2D(order, ⌊(lng+180)/360·2^o⌋, ⌊(90−lat)/180·2^o⌋)
cell of d              = hilbertD2XY(order, d)  →  bounds
zoom out one level     = ⌊d / 4⌋           zoom in one level = 4d … 4d+3
block at level m       = [p·4^m , (p+1)·4^m − 1]
query                  = bbox → cell rect → quadtree cover (≤ R ranges) → BETWEEN … OR … → exact filter
rule of thumb          = bits = 2·order ;  cell ≈ 40,075 km / 2^order wide at the equator
```

---

*Companion files in this folder: [`HILBERT_CURVE.html`](HILBERT_CURVE.html) (interactive version with live curve viewer, range-query comparison and a world-map demo), [`LOCATION_DB.html`](LOCATION_DB.html), [`GEOHASH_MAP_RENDERING.html`](GEOHASH_MAP_RENDERING.html).*
