package dev.yharnamdice.physics;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/** Server-side fixed-step roll. Positions and velocities are measured in table-local blocks. */
public final class DicePhysicsSession {
    public static final int MAX_DICE = 20;
    private static final double END = .89;
    private static final double SIDE = .40;
    private static final double STEP = 1.0 / 120;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3[] FACES = {
            UP, new Vec3(0,0,1), new Vec3(1,0,0), new Vec3(-1,0,0), new Vec3(0,0,-1), new Vec3(0,-1,0)
    };

    private final List<Die> dice;
    private final List<ImpactEvent> impacts = new ArrayList<>();
    private final double radius;
    private double accumulator;
    private double elapsed;
    private boolean complete;

    private DicePhysicsSession(int count, long seed, Vec3 launchDirection, List<Vec3> starts, double scale) {
        radius = .10 * scale;
        dice = new ArrayList<>(count);
        SplittableRandom random = new SplittableRandom(seed);
        Vec3 aim = new Vec3(launchDirection.x(),0,launchDirection.z()).normalized();
        Vec3 across = new Vec3(aim.z(),0,-aim.x());
        for (int i = 0; i < count; i++) {
            Vec3 start = starts != null && i < starts.size() ? starts.get(i) : new Vec3((i%5-2)*.15,0,-.29+(i/5)*.15);
            double x = Math.max(-END+radius, Math.min(END-radius,start.x()));
            double z = Math.max(-SIDE+radius, Math.min(SIDE-radius,start.z()));
            Vec3 velocity = aim.multiply(random.nextDouble(.78,1.47))
                    .add(across.multiply(random.nextDouble(-.45,.45)))
                    .add(UP.multiply(random.nextDouble(1.19,1.7)));
            Vec3 spin = new Vec3(random.nextDouble(-13,13),random.nextDouble(-17,17),random.nextDouble(-13,13));
            Quaternion rotation = randomOrientation(random);
            dice.add(new Die(i,new Vec3(x,Math.max(start.y(),support(rotation,UP)),z),rotation,velocity,spin));
        }
    }

    public static DicePhysicsSession throwDice(int count, long seed, Vec3 launchDirection) {
        return throwDice(count,seed,launchDirection,null,1);
    }

    public static DicePhysicsSession throwDice(int count, long seed, Vec3 launchDirection, List<Vec3> starts) {
        return throwDice(count,seed,launchDirection,starts,1);
    }

    public static DicePhysicsSession throwDice(int count, long seed, Vec3 direction, List<Vec3> starts, double scale) {
        if (count < 1 || count > MAX_DICE || !(scale > 0 && scale <= 1)) throw new IllegalArgumentException("dice size or count");
        return new DicePhysicsSession(count,seed,direction,starts,scale);
    }

    private static Quaternion randomOrientation(SplittableRandom random) {
        double r = random.nextDouble(), a = random.nextDouble(Math.PI * 2), b = random.nextDouble(Math.PI * 2);
        return new Quaternion(Math.sqrt(1-r)*Math.sin(a), Math.sqrt(1-r)*Math.cos(a),
                Math.sqrt(r)*Math.sin(b), Math.sqrt(r)*Math.cos(b));
    }

    public void tick(double seconds) {
        if (complete) return;
        accumulator += Math.max(0, Math.min(seconds, .25));
        impacts.clear();
        while (accumulator >= STEP && !complete) {
            advance();
            accumulator -= STEP;
        }
    }

    private void advance() {
        elapsed += STEP;
        for (Die die : dice) {
            if (die.settled) continue;
            die.velocity = die.velocity.add(new Vec3(0,-7.8*STEP,0));
            die.position = die.position.add(die.velocity.multiply(STEP));
            die.rotation = Quaternion.integrate(die.rotation,die.spin,STEP);
            die.spin = die.spin.multiply(.998);
        }
        solveDieContacts();
        complete = true;
        for (Die die : dice) {
            if (!die.settled) solveArena(die);
            complete &= die.settled;
        }
        if (!complete && elapsed >= 4.55) {
            dice.forEach(this::settle);
            complete = true;
        }
    }

    private double support(Quaternion q, Vec3 axis) {
        Vec3 local = q.conjugate().rotate(axis);
        double a = Math.abs(local.x()), b = Math.abs(local.y()), c = Math.abs(local.z());
        // Convex support of the original 26-face chamfered die: full extent in one axis per vertex.
        return radius * (.8375 * (a+b+c) + .1625 * Math.max(a, Math.max(b,c)));
    }

    private void solveArena(Die die) {
        Vec3 p = die.position;
        Vec3 xAxis = new Vec3(1,0,0), zAxis = new Vec3(0,0,1);
        double ex = support(die.rotation,xAxis), ez = support(die.rotation,zAxis);
        double x = Math.max(-END+ex,Math.min(END-ex,p.x()));
        double z = Math.max(-SIDE+ez,Math.min(SIDE-ez,p.z()));
        if (x != p.x()) bounce(die, xAxis.multiply(p.x() > 0 ? -1 : 1), ImpactEvent.Type.RIM, .49);
        if (z != p.z()) bounce(die, zAxis.multiply(p.z() > 0 ? -1 : 1), ImpactEvent.Type.RIM, .49);

        double floor = support(die.rotation,UP);
        die.position = new Vec3(x,Math.max(floor,p.y()),z);
        if (p.y() <= floor+.004) {
            if (die.velocity.y() < -.27) {
                bounce(die,UP,ImpactEvent.Type.TABLE,.38);
                die.spin = die.spin.multiply(.77);
            } else {
                die.velocity = new Vec3(die.velocity.x()*.935,0,die.velocity.z()*.935);
                die.spin = die.spin.multiply(.91);
                if (die.velocity.lengthSquared() < .018 && die.spin.lengthSquared() < 3) {
                    Quaternion flat = restingRotation(die.rotation, face(die.rotation));
                    die.rotation = slerp(die.rotation,flat,.11);
                    die.still += STEP;
                    if (die.still > .29) settle(die);
                } else {
                    die.still = 0;
                }
            }
        }
    }

    private void bounce(Die die, Vec3 normal, ImpactEvent.Type type, double restitution) {
        double inward = die.velocity.dot(normal);
        if (inward >= 0) return;
        Vec3 tangent = die.velocity.subtract(normal.multiply(inward));
        die.velocity = tangent.multiply(.78).subtract(normal.multiply(inward*restitution));
        die.spin = die.spin.add(normal.cross(tangent).multiply(-1.3));
        if (inward < -.17) impacts.add(new ImpactEvent(die.id,type,Math.min(1,-inward/2.8)));
    }

    private void solveDieContacts() {
        double distance = 1.93 * radius;
        for (int i = 0; i < dice.size(); i++) for (int j = i + 1; j < dice.size(); j++) {
            Die a = dice.get(i), b = dice.get(j);
            if (a.settled && b.settled) continue;
            Vec3 delta = b.position.subtract(a.position);
            double squared = delta.lengthSquared();
            if (squared >= distance*distance || squared < 1e-9) continue;
            double length = Math.sqrt(squared);
            Vec3 normal = delta.multiply(1/length);
            double intrusion = distance-length;
            if (!a.settled) a.position = a.position.subtract(normal.multiply(intrusion*(b.settled?1:.5)));
            if (!b.settled) b.position = b.position.add(normal.multiply(intrusion*(a.settled?1:.5)));
            double approach = b.velocity.subtract(a.velocity).dot(normal);
            if (approach >= -.03) continue;
            Vec3 impulse = normal.multiply(-.63*approach);
            if (!a.settled) a.velocity = a.velocity.subtract(impulse);
            if (!b.settled) b.velocity = b.velocity.add(impulse);
            if (approach < -.19) impacts.add(new ImpactEvent(a.id,ImpactEvent.Type.DICE,Math.min(1,-approach)));
        }
    }

    private void settle(Die die) {
        die.result=face(die.rotation);
        die.rotation=restingRotation(die.rotation,die.result);
        double x=support(die.rotation,new Vec3(1,0,0)), z=support(die.rotation,new Vec3(0,0,1));
        die.position=new Vec3(Math.max(-END+x,Math.min(END-x,die.position.x())),radius,
                Math.max(-SIDE+z,Math.min(SIDE-z,die.position.z())));
        die.velocity=die.spin=Vec3.ZERO;
        die.settled=true;
    }

    private static int face(Quaternion orientation) {
        Vec3 localUp=orientation.conjugate().rotate(UP);
        int best=0;
        for (int i=1;i<FACES.length;i++) if (localUp.dot(FACES[i])>localUp.dot(FACES[best])) best=i;
        return best+1;
    }

    private static Quaternion restingRotation(Quaternion orientation,int face) {
        Vec3 current=orientation.rotate(FACES[face-1]);
        Vec3 axis=current.cross(UP);
        if (axis.lengthSquared()<1e-14) return orientation;
        return Quaternion.axisAngle(axis,Math.acos(Math.max(-1,Math.min(1,current.dot(UP))))).multiply(orientation).normalized();
    }

    private static Quaternion slerp(Quaternion q,Quaternion t,double progress) {
        double cosine=q.x()*t.x()+q.y()*t.y()+q.z()*t.z()+q.w()*t.w();
        double a=1-progress, b=progress;
        if (Math.abs(cosine)<.9995) {
            double omega=Math.acos(Math.abs(cosine));
            a=Math.sin(a*omega)/Math.sin(omega);
            b=Math.sin(b*omega)/Math.sin(omega);
        }
        if (cosine<0) b=-b;
        return new Quaternion(a*q.x()+b*t.x(),a*q.y()+b*t.y(),a*q.z()+b*t.z(),a*q.w()+b*t.w()).normalized();
    }

    public boolean isComplete() { return complete; }
    public double elapsedSeconds() { return elapsed; }

    public List<DiceSnapshot> snapshots() {
        List<DiceSnapshot> state=new ArrayList<>(dice.size());
        for (Die die : dice) state.add(new DiceSnapshot(die.id,die.position,die.rotation,die.velocity,die.spin,die.result,die.settled));
        return List.copyOf(state);
    }

    public List<ImpactEvent> drainImpacts() {
        List<ImpactEvent> result=List.copyOf(impacts);
        impacts.clear();
        return result;
    }

    private static final class Die {
        final int id;
        Vec3 position;
        Quaternion rotation;
        Vec3 velocity;
        Vec3 spin;
        double still;
        int result;
        boolean settled;

        Die(int id,Vec3 position,Quaternion rotation,Vec3 velocity,Vec3 spin) {
            this.id=id;
            this.position=position;
            this.rotation=rotation;
            this.velocity=velocity;
            this.spin=spin;
        }
    }
}
