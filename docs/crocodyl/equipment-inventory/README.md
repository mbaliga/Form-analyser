# Athlete Equipment Inventory — product and data brief

## Product idea

A visual, game-like equipment room where an athlete can browse a market catalogue, save items to their personal locker, assemble a sport-specific loadout, and see which exact setup was used in each training or competition session. The inventory is the equipment layer for later performance analysis: each result should point to the athlete's actual item instances and setup values, rather than only to broad product names.

## Core records

Keep four kinds of records separate so the data remains useful as the catalogue grows:

1. **Product model** — a manufacturer model and its stable specifications (maker, model name, category, materials, construction, standards, model year, dimensions).
2. **Variant** — the selectable configuration that changes the equipment (hand, size, limb length and draw weight, rod length, tab size, color, etc.), with a manufacturer or retailer SKU when available.
3. **Market offer** — a vendor's listing for a product/variant, with region, currency, price, stock status, product URL, image URL, and observation time. Multiple retailers can offer the same product.
4. **Athlete item** — the athlete's own physical instance, with purchase/acquisition date, serial or notes, tuning values, wear/maintenance, and status. It may point to a product variant, or remain a custom/unidentified item.

Do not treat store listings, colors, or sizes as separate product models. Retain them as variants or offers so filters can show both model counts and purchasable options.

## Loadout and performance links

A **loadout** is a named collection of athlete items assigned to sport-specific equipment slots. Keep it versioned or snapshot it at session time, so later edits do not rewrite history. A **session equipment snapshot** records item IDs and the settings actually used (for example, bow draw weight at the fingers, stabiliser lengths and weights, string material/strand count, tab face, arrow setup; or climbing shoe model/size and chalk; or fencing weapon, blade, mask and protective kit). Link performance outcomes to that snapshot and the session context. This makes comparisons possible while avoiding an unsupported claim that equipment alone caused a change in performance.

## Sport and slot taxonomy

### Recurve archery

- Bow: riser, limbs, string, nocking points
- Tuning: sight, clicker, plunger, rest, arrows
- Stabilisation: long rod, extender, side rods, v-bar, weights, dampers
- Carry: quiver, belt, backpack, bow case, arrow tube
- Athlete: finger tab, finger sling, armguard, chest guard, clothing, shoes

### Fencing

- Weapon by discipline: foil, épée, sabre; blade, guard, grip, point and body/weapon cords
- Protection: mask, jacket, plastron, breeches, glove, chest protector, lamé/electric vest
- Carry and care: weapon bag, kit bag, tools and spare parts
- Athlete: fencing shoes, socks and clothing

### Bouldering

- Climbing shoes (model, size, closure, fit notes)
- Chalk and chalk bag/bucket; brushes and skin care
- Crash pad(s), carry gear and clothing
- Session context: gym/outdoor, wall/area, temperature and route/problem identifier

Each sport should define its own slots and required/optional status. Let an athlete add a custom slot or an item that has no catalogue match.

## Product discovery and coverage

Start with product feeds or retailer/manufacturer catalogues where available. Record the source and last-seen date for every field; mark unknown specs instead of inferring them. Merge duplicate retailer listings by manufacturer/model/variant evidence, retaining each seller's offer.

The initial auditable coverage set uses major retailers selected for the US and EU markets. The [coverage registry](market-coverage.json) records source URLs, visible category listing counts, item record counts and coverage status per retailer. The visual viewer is [catalogue.html](catalogue.html), and the machine-readable data is [catalogue.json](catalogue.json).

Current sources and status:

- **Archery:** Lancaster Archery Supply (US) and Target Archery (EU). The catalogue contains 480 Lancaster records and 13 Target Archery records. These are partial collections; category pages/options remain to be captured.
- **Bouldering:** REI (US) and Bergfreunde (EU). All 26 currently indexed REI Bouldering Gear listings are represented. Bergfreunde contributes 81 named shoe/accessory records from visible product pages. Neither constitutes every listing or every size/color offer across those retailers.
- **Fencing:** Absolute Fencing Gear (US, 20 records), Allstar USA (US, 7), PBT Fencing (EU, 16), and Leon Paul USA (US, 16). These records cover selected masks, uniform items and accessories; remaining categories and listings are not yet captured.
- **Additional retailers identified, not yet catalogued:** Blue Gauntlet, Europe Archery and Decathlon.

This is a useful visual inventory, not yet an exhaustive market catalogue for the named retailers. Source counts are not catalogue counts. Product options that are not verified remain on source pages; availability, price and stock are not asserted. Continue with permitted retailer feeds and product-by-product verification, retaining source and retrieval details for each model and variant. Product images are linked remotely to their source pages and are not packaged in this repository.

## Initial acceptance criteria

- Catalogue cards show a useful visual, model, maker, equipment category and source.
- Search and filters work by sport, slot, maker, product family, hand, size, market and source.
- One model can have many variants and many retailer offers without duplicate model cards.
- Athletes can save products to their locker, mark owned/borrowed/retired, and create a loadout.
- A session can snapshot its exact equipment and setup, with a path to the performance record.
- Coverage dashboards distinguish verified products, variants, offers and sources, and show freshness and gaps.
- Every uncertain or missing spec is explicit; the interface never silently invents a value.

## Current catalogue

As observed on 2026-11-16, the catalogue contains **659 source-linked records**: 493 archery, 107 bouldering and 59 fencing. There are 63 remote image links. The HTML viewer filters by sport, equipment slot, maker, market and search term. JSON records retain product model IDs, selected variant attributes, seller, market, source URL and observation date where captured. The viewer and data are kept in sync; detailed coverage limitations are in `market-coverage.json`.
