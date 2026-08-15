# Living Cities — ChatGPT implementation

Minecraft Java 1.21.1 + NeoForge.

## Alpha 1 goal
Turn arbitrary player-built structures into registered city buildings without prefab architecture.

Implemented in this first milestone:
- City Hall Core
- city creation and persistent ownership
- chunk territory and expansion costs
- City Planner point A / point B selection
- arbitrary custom-building registration
- Residential / Commercial / Office / Industrial zoning
- automatic basic floor detection
- usable-area based housing/jobs
- virtual population, taxes and treasury
- GitHub Actions build that uploads an installable jar

## Quick test
1. Get `City Hall Core` and `City Planner` from Creative inventory.
2. Place the City Hall Core near where you want the city center.
3. `/livingcities create My City`
4. Build/claim additional chunks with `/livingcities claim` while standing in an adjacent chunk.
5. With the City Planner: right-click point A, sneak-right-click point B.
6. `/livingcities building add residential My Apartments`
7. `/livingcities status`

Building types: `residential`, `commercial`, `office`, `industrial`.

This is intentionally Alpha 1. NPCs, utilities, industry production, services, diplomacy, traffic and advanced infrastructure belong to later milestones.
