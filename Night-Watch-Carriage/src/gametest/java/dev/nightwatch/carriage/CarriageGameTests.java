package dev.nightwatch.carriage;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Server-side contracts for seating, authority, movement, and persistent carriage state. */
public final class CarriageGameTests implements FabricGameTest {
    private static final BlockPos ORIGIN = new BlockPos(2, 1, 2);

    private static CarriageEntity carriage(TestContext context) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 7; z++) {
                context.setBlockState(ORIGIN.add(x, -1, z), Blocks.STONE);
                for (int y = 0; y <= 3; y++) context.setBlockState(ORIGIN.add(x, y, z), Blocks.AIR);
            }
        }
        CarriageEntity carriage = NightWatchCarriage.CARRIAGE.create(context.getWorld());
        context.assertTrue(carriage != null, "entity type must create a carriage");
        carriage.refreshPositionAndAngles(context.getAbsolutePos(ORIGIN).getX() + .5,
            context.getAbsolutePos(ORIGIN).getY(), context.getAbsolutePos(ORIGIN).getZ() + .5, 0, 0);
        context.assertTrue(context.getWorld().spawnEntity(carriage), "carriage must spawn");
        return carriage;
    }

    private static ServerPlayerEntity player(TestContext context, double x, double y, double z) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.refreshPositionAndAngles(context.getAbsolutePos(ORIGIN).getX() + x,
            context.getAbsolutePos(ORIGIN).getY() + y, context.getAbsolutePos(ORIGIN).getZ() + z, 0, 0);
        return player;
    }

    private static void put(TestContext context, CarriageEntity carriage, ServerPlayerEntity player,
                            CarriageEntity.Seat seat) {
        context.assertTrue(carriage.moveToSeat(player, seat), "player must enter the requested free seat");
        context.assertTrue(carriage.seatOf(player) == seat, "server seat tracker must match the requested seat");
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 35, batchId = "night_watch_carriage_seats")
    public void seatsAssignExactlyOnceAndCannotKickAnOccupant(TestContext context) {
        CarriageEntity carriage = carriage(context);
        ServerPlayerEntity left = player(context, .4, 1, -.5);
        ServerPlayerEntity right = player(context, -.4, 1, -.5);
        ServerPlayerEntity puller = player(context, 0, 0, 2.4);
        ServerPlayerEntity fourth = player(context, 0, 1, 1.4);
        put(context, carriage, left, CarriageEntity.Seat.LEFT);
        put(context, carriage, right, CarriageEntity.Seat.RIGHT);
        put(context, carriage, puller, CarriageEntity.Seat.PULLER);
        context.assertTrue(!carriage.moveToSeat(fourth, CarriageEntity.Seat.LEFT),
            "a fourth player cannot displace the left occupant");
        context.assertTrue(carriage.occupant(CarriageEntity.Seat.LEFT) == left.getId(),
            "left occupant remains after a denied seat request");
        context.assertTrue(carriage.getPassengerList().size() == 3,
            "carriage never has more than two passengers and one puller");
        right.stopRiding();
        put(context, carriage, left, CarriageEntity.Seat.RIGHT);
        context.assertTrue(carriage.occupant(CarriageEntity.Seat.LEFT) < 0 &&
            carriage.controlSeat() == CarriageEntity.Seat.PULLER, "switching seats releases left-seat authority");
        put(context, carriage, fourth, CarriageEntity.Seat.LEFT);
        context.assertTrue(carriage.controlSeat() == CarriageEntity.Seat.LEFT, "new left rider becomes driver");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 40, batchId = "night_watch_carriage_authority")
    public void leftSeatOwnsAuthorityUntilItDelegatesToPuller(TestContext context) {
        CarriageEntity carriage = carriage(context);
        ServerPlayerEntity left = player(context, .4, 1, -.5);
        ServerPlayerEntity right = player(context, -.4, 1, -.5);
        ServerPlayerEntity puller = player(context, 0, 0, 2.4);
        put(context, carriage, left, CarriageEntity.Seat.LEFT);
        put(context, carriage, right, CarriageEntity.Seat.RIGHT);
        put(context, carriage, puller, CarriageEntity.Seat.PULLER);
        context.assertTrue(carriage.controlSeat() == CarriageEntity.Seat.LEFT,
            "left passenger controls by default");
        carriage.acceptInput(right, 1);
        context.runAtTick(5, () -> {
            context.assertTrue(carriage.speed() == 0f, "right passenger cannot send drive input");
            carriage.acceptAction(right, CarriagePackets.Action.TOGGLE_DRIVER);
            context.assertTrue(carriage.controlSeat() == CarriageEntity.Seat.LEFT,
                "right passenger cannot transfer authority");
            carriage.acceptAction(left, CarriagePackets.Action.TOGGLE_DRIVER);
            context.assertTrue(carriage.controlSeat() == CarriageEntity.Seat.PULLER,
                "left passenger can delegate authority to the puller");
            carriage.acceptInput(left, 1);
        });
        context.runAtTick(8, () -> {
            context.assertTrue(carriage.speed() == 0f, "former driver input is ignored after delegation");
            carriage.acceptInput(puller, 1);
        });
        context.runAtTick(12, () -> {
            context.assertTrue(carriage.speed() > 0f, "puller input moves the carriage after delegation");
        });
        context.runAtTick(25, () -> {
            context.assertTrue(carriage.speed() == 0f, "carriage stops once drive packets time out");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 60, batchId = "night_watch_carriage_motion")
    public void carriageNeedsPullerAndReportsMovementSpeed(TestContext context) {
        CarriageEntity carriage = carriage(context);
        ServerPlayerEntity left = player(context, .4, 1, -.5);
        put(context, carriage, left, CarriageEntity.Seat.LEFT);
        carriage.acceptInput(left, 1);
        Vec3d parked = carriage.getPos();
        ServerPlayerEntity puller = player(context, 1.9, 0, 2.4);
        context.runAtTick(7, () -> {
            context.assertTrue(carriage.speed() == 0f && parked.squaredDistanceTo(carriage.getPos()) < .001,
                "the left passenger cannot move an unhitched carriage");
            put(context, carriage, puller, CarriageEntity.Seat.PULLER);
            carriage.acceptInput(left, 1);
        });
        context.runAtTick(11, () -> {
            context.assertTrue(carriage.speed() > 0f && carriage.getZ() > parked.z,
                "the default left driver can move after a puller hitches");
            puller.stopRiding();
        });
        context.runAtTick(14, () -> {
            context.assertTrue(carriage.speed() == 0f, "dismounting from the harness stops the carriage");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 65, batchId = "night_watch_carriage_steps")
    public void climbsOneBlockAndRollsBackDown(TestContext context) {
        CarriageEntity carriage = carriage(context);
        ServerPlayerEntity puller = player(context, 0, 0, 2.4);
        put(context, carriage, puller, CarriageEntity.Seat.PULLER);
        Vec3d start = carriage.getPos();
        for (int x = -2; x <= 2; x++) {
            for (int z = 4; z <= 5; z++) context.setBlockState(ORIGIN.add(x, 0, z), Blocks.STONE);
            for (int z = 8; z <= 12; z++) context.setBlockState(ORIGIN.add(x, -1, z), Blocks.STONE);
        }
        boolean[] reached = new boolean[2];
        for (int tick = 1; tick <= 38; tick++) {
            context.runAtTick(tick, () -> {
                if (puller.getY() > start.y + .99 && carriage.getY() < start.y + .01) reached[0] = true;
                if (carriage.getY() > start.y + .99) reached[1] = true;
                carriage.acceptInput(puller, 1);
            });
        }
        context.runAtTick(46, () -> {
            context.assertTrue(reached[0], "the puller steps onto the road before the wheels");
            context.assertTrue(reached[1], "the wheels must climb the full block");
            context.assertTrue(carriage.getZ() > start.z + 7 && Math.abs(carriage.getY() - start.y) < .01,
                "the carriage rolls back down to the original road height, delta " + carriage.getPos().subtract(start));
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 50, batchId = "night_watch_carriage_walls")
    public void cannotClimbATwoBlockWall(TestContext context) {
        CarriageEntity carriage = carriage(context);
        ServerPlayerEntity puller = player(context, 0, 0, 2.4);
        put(context, carriage, puller, CarriageEntity.Seat.PULLER);
        Vec3d start = carriage.getPos();
        for (int x = -2; x <= 2; x++) {
            context.setBlockState(ORIGIN.add(x, 0, 4), Blocks.STONE);
            context.setBlockState(ORIGIN.add(x, 1, 4), Blocks.STONE);
        }
        for (int tick = 1; tick <= 30; tick++) context.runAtTick(tick, () -> carriage.acceptInput(puller, 1));
        context.runAtTick(32, () -> {
            context.assertTrue(puller.getZ() < start.z + 3.5 && Math.abs(carriage.getY() - start.y) < .01,
                "a two-block wall stops the harness rider and the carriage");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 45, batchId = "night_watch_carriage_controls")
    public void roofAndLampsToggleAndPersistThroughNbt(TestContext context) {
        CarriageEntity carriage = carriage(context);
        ServerPlayerEntity left = player(context, .4, 1, -.5);
        put(context, carriage, left, CarriageEntity.Seat.LEFT);
        context.assertTrue(carriage.hoodRaised() && !carriage.lanternsOn(),
            "fresh carriage starts covered with lamps off");
        carriage.acceptAction(left, CarriagePackets.Action.TOGGLE_HOOD);
        carriage.acceptAction(left, CarriagePackets.Action.TOGGLE_LAMPS);
        context.assertTrue(!carriage.hoodRaised() && carriage.lanternsOn(),
            "authorized passenger toggles roof and lantern state");
        NbtCompound saved = carriage.writeNbt(new NbtCompound());
        saved.remove("UUID");
        carriage.discard();
        CarriageEntity loaded = NightWatchCarriage.CARRIAGE.create(context.getWorld());
        context.assertTrue(loaded != null, "entity type must recreate a saved carriage");
        loaded.readNbt(saved);
        context.assertTrue(!loaded.hoodRaised() && loaded.lanternsOn(),
            "roof and lamp state survive NBT round-trip");
        context.assertTrue(context.getWorld().spawnEntity(loaded), "restored carriage must spawn");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 35, batchId = "night_watch_carriage_dashboard")
    public void dashboardActionsRequireASeat(TestContext context) {
        CarriageEntity carriage = carriage(context);
        ServerPlayerEntity nearby = player(context, 0, 0, 2.4);
        context.assertTrue(carriage.canBoardFrom(nearby), "player starts within boarding range");
        for (CarriagePackets.Action action : CarriagePackets.Action.values()) {
            carriage.acceptAction(nearby, action);
        }
        context.assertTrue(!nearby.hasVehicle() && !carriage.hasPassengers(),
            "dashboard seat buttons cannot board from outside");
        context.assertTrue(carriage.hoodRaised() && !carriage.lanternsOn(),
            "an outside player cannot change the roof or lamps");
        clickSeat(context, carriage, nearby, CarriageEntity.Seat.PULLER);
        context.assertTrue(carriage.seatOf(nearby) == CarriageEntity.Seat.PULLER, "right click still boards");
        carriage.acceptAction(nearby, CarriagePackets.Action.SEAT_LEFT);
        context.assertTrue(carriage.seatOf(nearby) == CarriageEntity.Seat.LEFT, "an occupant can use the seat buttons");
        nearby.stopRiding();
        carriage.acceptAction(nearby, CarriagePackets.Action.TOGGLE_LAMPS);
        context.assertTrue(!carriage.lanternsOn(), "a late dashboard action is ignored after dismount");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 35, batchId = "night_watch_carriage_boarding")
    public void worldClickUsesTheHitSeatWithoutFallback(TestContext context) {
        CarriageEntity carriage = carriage(context);
        carriage.setYaw(67);
        ServerPlayerEntity first = player(context, 0, 0, 0);
        ServerPlayerEntity second = player(context, 0, 0, 0);
        ServerPlayerEntity puller = player(context, 0, 0, 0);
        Vec3d front = carriage.getPos().add(new Vec3d(0, 0, 2.15).rotateY(-67 * MathHelper.RADIANS_PER_DEGREE));
        first.setPosition(front);
        second.setPosition(front);
        puller.setPosition(front);

        carriage.interact(first, Hand.MAIN_HAND);
        carriage.interactAt(first, new Vec3d(0, .5, 0), Hand.MAIN_HAND);
        context.assertTrue(!first.hasVehicle(), "clicking the body does not pick a seat");

        Vec3d cushion = carriage.boardingPoint(CarriageEntity.Seat.RIGHT, 1);
        context.assertTrue(carriage.raycastBoarding(cushion.add(0, 2, 0), cushion, 1) == null,
            "a raised canopy obscures the seat from above");
        clickSeat(context, carriage, first, CarriageEntity.Seat.RIGHT);
        context.assertTrue(carriage.seatOf(first) == CarriageEntity.Seat.RIGHT &&
            carriage.occupant(CarriageEntity.Seat.LEFT) < 0, "right click takes the right seat even when left is free");

        clickSeat(context, carriage, second, CarriageEntity.Seat.RIGHT);
        context.assertTrue(!second.hasVehicle() && carriage.seatOf(first) == CarriageEntity.Seat.RIGHT &&
            carriage.occupant(CarriageEntity.Seat.LEFT) < 0, "an occupied seat neither ejects nor redirects");

        clickSeat(context, carriage, second, CarriageEntity.Seat.LEFT);
        clickSeat(context, carriage, puller, CarriageEntity.Seat.PULLER);
        context.assertTrue(carriage.seatOf(second) == CarriageEntity.Seat.LEFT &&
            carriage.seatOf(puller) == CarriageEntity.Seat.PULLER, "left and harness clicks select their exact places");
        context.complete();
    }

    private static void clickSeat(TestContext context, CarriageEntity carriage, ServerPlayerEntity player,
                                  CarriageEntity.Seat seat) {
        Vec3d aim = carriage.boardingPoint(seat, 1);
        CarriageEntity.BoardingHit hit = carriage.raycastBoarding(player.getEyePos(), aim, 1);
        context.assertTrue(hit != null && hit.seat() == seat, "expected ray hit on " + seat);
        carriage.interactAt(player, hit.point().subtract(carriage.getPos()), Hand.MAIN_HAND);
    }
}
