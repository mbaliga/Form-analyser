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

Initial public market sources identified:

- US recurve breadth: Lancaster Archery category and product pages — `https://lancasterarchery.com/collections/olympic-recurve`, with dedicated riser, limb, stabiliser, case, backpack, quiver, string and tab categories.
- EU target archery: Target Archery EU — `https://target-archery.eu/` (public catalogue reports 455 products in the source index checked for this brief); Europe Archery — `https://www.europearchery.com/`.
- Fencing examples for a later sport pack: German Fencing Gear — `https://germanfencinggear.com/`; Fechtwelt — `https://fechtwelt.de/en_GB`; Leon Paul — `https://www.leonpaul.com/`; PBT — `https://shop.pbtfencing.com/`.

Treat US and EU as markets, not just retailer locations: an offer should record shipping destination/availability, currency, vendor country, tax treatment where displayed, and the date observed. Do not promise exhaustive coverage until the sources and update pipeline can demonstrate it. Respect retailer terms and image licensing. Prefer manufacturer media feeds or stable linked image URLs and store image provenance; do not package copied images without a clear reuse basis.

## Initial acceptance criteria

- Catalogue cards show a useful visual, model, maker, equipment category and source.
- Search and filters work by sport, slot, maker, product family, hand, size, market and source.
- One model can have many variants and many retailer offers without duplicate model cards.
- Athletes can save products to their locker, mark owned/borrowed/retired, and create a loadout.
- A session can snapshot its exact equipment and setup, with a path to the performance record.
- Coverage dashboards distinguish verified products, variants, offers and sources, and show freshness and gaps.
- Every uncertain or missing spec is explicit; the interface never silently invents a value.

## Current catalogue slice

The branch now includes a source-linked visual catalogue and a structured JSON dataset. The current curated slice contains 480 entries:

- 50 riser listings from Lancaster Archery, with retailer-hosted photos.
- 324 ILF limb length/marked-weight options across nine model families, expanded only from product-page ranges and increments.
- 30 Lancaster target stabiliser listings and 30 quiver entries.
- 6 backpack, 7 case, 3 string and 30 finger-tab entries.

The HTML viewer filters by equipment slot, maker, market and search term. The JSON keeps product links, image URLs, per-entry specs, seller and observation date. Fifty-eight entries currently have verified remote image URLs. Images are hotlinked from the source sites and are not packaged in this repository.

This is a curated market slice, not a complete catalogue of every product currently sold in the US and EU. Direct retailer collection extraction was rate-limited during this update, and public category pages expose fewer individually verifiable products for backpacks, cases and strings. The catalogue therefore makes its coverage counts visible and links users to the source category or product page where an individual image or specific variant remains unverified. Availability, price and stock are not asserted. Continue with permitted manufacturer data, retailer feeds or product-by-product verification, retaining source and retrieval details for every added model and variant.
