# Composite prototype runtime

The first set uses one root block and item per user type. UUID ownership, pose,
art form and declared BASE/ALT profile persist separately from source carrier IDs.
Descriptors are under `src/architecture/resources/bloodborne_dw/composite/`.
This runtime is a first-set prototype; its numeric catalog IDs and city conversion
remain unfrozen.

| Concern | Current contract | Evidence or limit |
| --- | --- | --- |
| Visual geometry | Root BER renders original baked source parts, UV and intrinsic element rotations. Part offset, source blockstate yaw/pitch, authored extra leaf yaw, global45 rotation and source anchor compensation are distinct transforms. | Tree18 parts/12 source models, source x0/y0\|180\|270/uvlockfalse; `TREE_PROTOTYPE_RESOURCES.json`. Client visual acceptance remains separate. |
| Physical shape | Descriptor collision boxes only; not visual bounds. Shared contributions form a union. Thin diagonal segments stay sparse. Native collision is evaluated under a nested-safe native-only guard before physical owner masks are added. | Native16³ occupancy construction, cached shapes, outward approximation less than1/16 block per face; no repeated strip unions. Both collision overloads include overlays. A native block whose collision delegates to outline cannot accidentally turn owner selection into solid physics. |
| Selection | Separate descriptor boxes, per-owner ray intersections, deterministic distance/UUID tie ordering. Foreign block outline wins when nearer or tied. Offhand builders cycle only the server's current ray owners; a separate S2C selection packet updates the caller. | Server ray tests use actual head direction. Client cycling never mutates selection locally, preventing integrated-server double cycling. Client manual synchronization remains NOT_RUN. |
| Reservations | Root and explicit essential cells only. Decorative/partial neighbor overlap is accepted by default, independent of physical overlap. | Nonroot essential offsets currently require cardinal turns;45 rejects them rather than using incorrect cell rotation. All first-set descriptors reserve root only. |
| Foreign cells | Actual foreign state and BE instance stay in the world. A per-dimension PersistentState ledger stores owner contributions and full cached masks. Only vanilla AIR/CAVE_AIR/VOID_AIR classify as free cells. | Native chest exact state/NBT/instance tests through place/open/rotate/remove. Source technical AirBlock remains foreign, retains emission9, and restores both empty shapes after overlap cleanup. No AABB replacement or clearing. |
| Atomic mutations | Core captures old state/opaque BE bytes and checks load status, bounds, permission, support and actual entity obstruction before writes. No forced chunk load. | `FabricCompositeWorld` separately captures full BE data and ledger. Partial failures use `restoreSilently`, including failed cell; stable updates publish after commit/rollback. |
| Cache refresh | Carrier state/bindings can remain identical while art/shape changes. Such writes remain in the plan and refresh incoming contributions. |19th pure Java regression checks cached metadata refresh and separate rollback. Attempt1 exposed stale helper shapes and was retained. |
| Support | Geometric centered top contact may be a slab/fence, with per-placement mount height. Removed or changed-height support removes the whole object once. | GameTests exercise native fence/top/bottom slab behavior; no full-cube-only support rule. Unloaded support defers validation. |
| Removal | Player break resolves selected owner; actorless native root/helper with one live owner also removes the whole object. Foreign/shared ambiguity requires a real target. | One art-preserving drop; other owners/foreign blocks stay. Stale replacement cleanup prunes exact old mask only. |
| Persistence | Root/helper BE NBT and foreign ledger retain UUID, cached masks and opaque payload. SourceShift applies only to reviewed source anchoring. | Native palette and `createNbtWithIdentifyingData` factory roundtrips; picks omit transient MountY/SourceShift and allocate new UUID when placed. |
| Activity | No entity or BlockEntityTicker. Server world tick only drains queued mutation/validation events. Source parts render from the root outside its cell bounds. | No continuously ticking object animations. Root/helper opacity0 and transparent flag avoid lighting outline queries. |
| Render features/lighting | Selected model closures contain no tintindex or cullface, so no source tint or neighbor culling is lost in this set. BER currently supplies root packed light to all parts. | `COMPOSITE_RENDER_FEATURES.json`; per-source-cell lighting/AO equivalence, shader shadows and client visuals are NOT_RUN. |
| Scale | Shape cache bounded8192, source-shift cache bounded4096. | Networking JOIN currently sends all ledger cells and CHUNK_LOAD scans all cells. Spatial indexing/watch-limited sync and measured city-scale performance are required before mass conversion. |

Tree geometry freezes the exact18-member example at the original coordinates.
The second confirmed example has identical relative models, transforms and art
choices, so it uses the same art form. Its9×18×9 visual bounds are not a solid
box and are not an ownership mask. For a reviewed source fixture, the owner is
the lowest actual melon cell atY119; SourceShift[0,-1,0] preserves the mesh while
keeping the grass cell atY118. Ordinary new builds use the canonical anchor and
do not inherit this compensation from pick/drop.

Actual original Forge1.18.2 measurements are in
`reports/SOURCE_REFERENCE_CLIENT_SWEPT.json` and `SOURCE_PHYSICS_CLIENT_SWEPT_ACTUAL.json`:
west foliage gap moves3m; central source wool and lower melon stop at1.2m.
The proposed0.25×0.25×6 lower stem, continuous narrow collision and passable upper
central billboard intentionally change those carriers. New movement checks keep
the west gap open and record the upper/lower policy. These changes require user
review under TASK§8; a server technical PASS does not approve them or prove
graphical/Creative UI/shader/two-client behavior.

Historical attempts are retained. Attempt2 hung in repeated fractional strip
union/simplify while removing a roof; thread dump established the cause. Bounded
occupancy construction fixes that first-build complexity. Some ray test failures
were mock-player setup errors: Minecraft `LivingEntity.getYaw(float)` uses head
yaw, so a fixture changing body yaw must also set head yaw. Production targeting
continues to use the native eye and rotation vector.

Attempts9/10 exposed two native shape paths in the compatibility-light overlap:
the two-argument collision overload returned a static cached shape, and the
default native collision implementation called the state's outline shape.
The runtime now bypasses that cache only for active overlays and suppresses
owner selection during native collision evaluation. The regression keeps an
exact narrow-stem check, checks both native query overloads, and verifies that
selection-only crown geometry stays passable over the source light.

Attempt14 passes all61 native GameTests and all19 pure transaction checks. On
production SHA `1c9e2788c79223072e89150c2fdbef159e6e226bb76a823cd1296ba9e43027f9`,
the V7 ordinary minimal client loads the derived scene, resolves all52 composite
source models,192 ladder states and512 representative wall states without a
missing model or sprite, then saves and exits normally. All16 explicitly added
own atlas sprites occur in actual quads and vanilla STONE retains its original
sprite; `BLOCK_ATLAS_RUNTIME_V7.json` independently checks the client output and
production JAR. This confirms resource loading, not manual visual acceptance.

The same V7 production JAR passes source authoring, production-only reopen and
persisted migration replay for the bounded41-object/110-member fixture. All four
independent post-save/reopen/replay comparisons pass: native foreign data,
ownership, cached masks, source provenance and live RP source envelopes persist.
The exact reopened world is archived with all generated terrain retained;
`FIRST_SET_SOURCE_REVIEW_SCENE_V7.json` records every file hash and matching
native/independent evidence gate. Proposed physical changes remain pending review.
