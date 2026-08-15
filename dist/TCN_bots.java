// TCN_bots - robo da equipe TCN-bots, Campeonato Robocode dos Colegios UniVap.
// A classe do robo e TCN_bots, declarada logo abaixo. As demais classes sao
// auxiliares dela e por isso estao aninhadas dentro dela: o arquivo tem uma
// unica classe de topo, com o nome da equipe, como o regulamento pede.
package TCN_bots;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import robocode.AdvancedRobot;
import robocode.Bullet;
import robocode.BulletHitBulletEvent;
import robocode.BulletHitEvent;
import robocode.DeathEvent;
import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.RobotDeathEvent;
import robocode.ScannedRobotEvent;
import robocode.WinEvent;
public class TCN_bots extends AdvancedRobot {
    static final Map<String, Enemy> KNOWN = new HashMap<String, Enemy>();
    static final boolean USE_BULLET_SHADOW = false;
    static final boolean USE_ACTIVE_PARRY  = false;
    private final Point2D.Double position = new Point2D.Double();
    private Rectangle2D.Double field;
    private Rectangle2D.Double safeField;
    private Profile profile;
    private final Movement movement = new Movement(this);
    private final Gun gun = new Gun(this);
    private final Surf surf = new Surf(this);
    private final Shield shield = new Shield(this);
    private Enemy target;
    public void run() {
        setColors(new Color(18, 20, 28),
                  new Color(200, 40, 60),
                  new Color(90, 200, 255),
                  new Color(255, 210, 90),
                  new Color(40, 90, 140));
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);
        field = new Rectangle2D.Double(Util.ROBOT_SIZE / 2, Util.ROBOT_SIZE / 2,
                                       getBattleFieldWidth()  - Util.ROBOT_SIZE,
                                       getBattleFieldHeight() - Util.ROBOT_SIZE);
        profile = Profile.forBattle(getBattleFieldWidth(), getBattleFieldHeight(), getOthers());
        double inset = profile.safeInset;
        safeField = new Rectangle2D.Double(field.x + inset, field.y + inset,
                                           Math.max(field.width  - 2 * inset, 1),
                                           Math.max(field.height - 2 * inset, 1));
        for (Enemy e : KNOWN.values()) {
            e.newRound();
        }
        movement.newRound();
        gun.newRound();
        surf.newRound();
        shield.newRound();
        while (true) {
            position.setLocation(getX(), getY());
            gun.update(getTime());
            surf.update(getTime());
            shield.update();
            chooseTarget();
            driveRadar();
            drive();
            aim();
            execute();
        }
    }
    public void onScannedRobot(ScannedRobotEvent e) {
        Enemy enemy = KNOWN.get(e.getName());
        if (enemy == null) {
            enemy = new Enemy(e.getName());
            KNOWN.put(e.getName(), enemy);
        }
        enemy.update(e, this);
        enemy.detectFiring();
        if (isDuel()) {
            surf.onEnemyFire(enemy);
        }
    }
    public void onRobotDeath(RobotDeathEvent e) {
        Enemy enemy = KNOWN.get(e.getName());
        if (enemy != null) {
            enemy.alive = false;
        }
    }
    public void onBulletHit(BulletHitEvent e) {
        Enemy enemy = KNOWN.get(e.getName());
        if (enemy != null) {
            enemy.syncEnergy(e.getEnergy());
        }
    }
    public void onHitByBullet(HitByBulletEvent e) {
        Enemy enemy = KNOWN.get(e.getName());
        if (enemy != null) {
            enemy.absorbKnownEnergyGain(Util.bulletReward(e.getPower()));
            surf.onHitByBullet(e, enemy, getTime());
        }
    }
    public void onBulletHitBullet(BulletHitBulletEvent e) {
        shield.onParrySuccess();
    }
    public void onHitRobot(HitRobotEvent e) {
        Enemy enemy = KNOWN.get(e.getName());
        if (enemy != null) {
            enemy.alive = true;
        }
    }
    public void onWin(WinEvent e) {
        setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
    }
    public void onDeath(DeathEvent e) {
    }
    private void chooseTarget() {
        Enemy best = null;
        double bestScore = Double.MAX_VALUE;
        long now = getTime();
        for (Enemy e : KNOWN.values()) {
            if (!e.alive || e.lastSeen < 0) continue;
            double score = e.distance;
            if (!e.isFresh(now)) score += 400;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        target = best;
    }
    private List<Enemy> liveEnemies() {
        List<Enemy> out = new ArrayList<Enemy>();
        for (Enemy e : KNOWN.values()) {
            if (e.alive && e.lastSeen >= 0) out.add(e);
        }
        return out;
    }
    private void driveRadar() {
        long now = getTime();
        if (getOthers() > 1 || target == null || !target.isFresh(now)) {
            setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            return;
        }
        double turn = Util.relative(target.absBearing - getRadarHeadingRadians());
        double margin = Math.atan(Util.ROBOT_SIZE / Math.max(target.distance, 100)) + 0.06;
        setTurnRadarRightRadians(turn + Util.sign(turn) * margin);
    }
    private void drive() {
        if (isDuel()) {
            surf.drive(target, getTime());
        } else {
            movement.driveMelee(liveEnemies(), getTime());
        }
    }
    boolean isDuel() {
        return getOthers() <= 1;
    }
    private void aim() {
        if (target == null || (getTime() - target.lastSeen) > 10) return;
        if (shouldParry() && shield.tryParry(surf.closestWave(getTime()), target, getTime())) {
            return;
        }
        gun.engage(target, choosePower(target), getTime());
    }
    private boolean shouldParry() {
        if (!USE_ACTIVE_PARRY || !isDuel() || target == null) return false;
        return target.distance < 250 || getEnergy() < 25;
    }
    private double choosePower(Enemy t) {
        double power;
        if (getOthers() > 1) {
            power = t.distance < profile.gunNear ? 1.9 : 1.2;
        } else {
            power = t.distance < profile.pointBlank ? 3.0
                  : (t.distance < profile.gunFar ? 2.2 : 1.6);
        }
        power *= profile.firePowerScale;
        power = Math.min(power, getEnergy() / 6.0);
        power = Math.min(power, t.energy / 4.0 + 0.1);
        if (getEnergy() < 4.0) power = Math.min(power, 0.5);
        if (power < 0.1) return 0;
        return Util.clamp(0.1, power, 3.0);
    }
    Point2D.Double myPosition() {
        return new Point2D.Double(getX(), getY());
    }
    Point2D.Double position() {
        return position;
    }
    Rectangle2D.Double safeField() {
        return safeField;
    }
    Rectangle2D.Double battleField() {
        return field;
    }
    Shield shield() {
        return shield;
    }
    Profile profile() {
        return profile;
    }
    static class Enemy {
        public final String name;
        public boolean alive;
        public long     lastSeen = -1;
        public Point2D.Double pos = new Point2D.Double();
        public double energy;
        public double heading;
        public double velocity;
        public double distance;
        public double absBearing;
        public double turnRate;
        public int lateralDirection = 1;
        public double lateralVelocity;
        public final float[][] gfSegments = new float[Gun.SEGMENTS][Gun.BINS];
        public final float[] gfGlobal = new float[Gun.BINS];
        public final int[] virtualHits  = new int[Gun.GUN_COUNT];
        public final int[] virtualShots = new int[Gun.GUN_COUNT];
        public final float[][] surfSegments = new float[Gun.SEGMENTS][Gun.BINS];
        public final float[]   surfGlobal   = new float[Gun.BINS];
        public Point2D.Double previousPos = new Point2D.Double();
        private double previousEnergy = 100.0;
        private double previousHeading;
        private long   previousScanTime = -1;
        public double justFiredPower;
        public Enemy(String name) {
            this.name = name;
            this.alive = true;
        }
        public void newRound() {
            alive = true;
            lastSeen = -1;
            previousScanTime = -1;
            previousEnergy = 100.0;
            energy = 100.0;
            justFiredPower = 0;
            turnRate = 0;
            lateralVelocity = 0;
        }
        public void update(ScannedRobotEvent e, TCN_bots self) {
            long now = e.getTime();
            absBearing = Util.absolute(self.getHeadingRadians() + e.getBearingRadians());
            distance   = e.getDistance();
            previousPos = pos;
            pos = Util.project(self.myPosition(), absBearing, distance);
            double newHeading = e.getHeadingRadians();
            if (previousScanTime >= 0 && now > previousScanTime) {
                turnRate = Util.relative(newHeading - previousHeading) / (now - previousScanTime);
            }
            previousHeading  = newHeading;
            previousScanTime = now;
            heading  = newHeading;
            velocity = e.getVelocity();
            energy   = e.getEnergy();
            lastSeen = now;
            alive    = true;
            lateralVelocity = velocity * Math.sin(heading - absBearing);
            if (Math.abs(lateralVelocity) > 0.15) {
                lateralDirection = Util.sign(lateralVelocity);
            }
        }
        public void detectFiring() {
            double drop = previousEnergy - energy;
            previousEnergy = energy;
            justFiredPower = (drop > 0.0999 && drop < 3.0001) ? drop : 0.0;
        }
        public void syncEnergy(double exact) {
            previousEnergy = exact;
            energy = exact;
        }
        public void absorbKnownEnergyGain(double amount) {
            previousEnergy += amount;
        }
        public boolean isFresh(long now) {
            return alive && lastSeen >= 0 && (now - lastSeen) <= 8;
        }
        public Point2D.Double predictedPosition(long now) {
            if (lastSeen < 0) return pos;
            long dt = Math.min(now - lastSeen, 20);
            return Util.project(pos, heading, velocity * dt);
        }
    }
    static class Gun {
        public static final int BINS   = 23;
        public static final int CENTER = BINS / 2;
        public static final int SEGMENTS = 9;
        public static final int HEAD_ON  = 0;
        public static final int LINEAR   = 1;
        public static final int CIRCULAR = 2;
        public static final int STATS    = 3;
        public static final int GUN_COUNT = 4;
        private static final double SEGMENT_CONFIDENCE = 6.0;
        private static final double GLOBAL_CONFIDENCE  = 3.0;
        private static final int MIN_SAMPLES = 10;
        private final TCN_bots bot;
        private final List<Wave> waves = new ArrayList<Wave>();
        public Gun(TCN_bots bot) {
            this.bot = bot;
        }
        public void newRound() {
            waves.clear();
        }
        public int segmentOf(Enemy e) {
            Profile p = bot.profile();
            int d = e.distance < p.gunNear ? 0 : (e.distance < p.gunFar ? 1 : 2);
            double lateral = Math.abs(e.lateralVelocity);
            int v = lateral < 2.0 ? 0 : (lateral < 5.5 ? 1 : 2);
            return d * 3 + v;
        }
        public Wave engage(Enemy target, double power, long now) {
            if (target == null || power <= 0) return null;
            double speed = Util.bulletSpeed(power);
            Point2D.Double me = bot.position();
            Point2D.Double enemyPos = target.predictedPosition(now);
            double directAngle = Util.angle(me, enemyPos);
            double escapeAngle = Util.maxEscapeAngle(speed);
            int    segment     = segmentOf(target);
            double[] angles = new double[GUN_COUNT];
            angles[HEAD_ON]  = directAngle;
            angles[LINEAR]   = predictAngle(me, target, speed, false);
            angles[CIRCULAR] = predictAngle(me, target, speed, true);
            angles[STATS]    = statisticalAngle(target, directAngle, escapeAngle, segment);
            int chosen = chooseGun(target);
            double aim = angles[chosen];
            double gunTurn = Util.relative(aim - bot.getGunHeadingRadians());
            bot.setTurnGunRightRadians(gunTurn);
            double tolerance = Math.atan(20.0 / Math.max(target.distance, 60));
            if (Math.abs(gunTurn) > tolerance) return null;
            if (bot.getGunHeat() > 0) return null;
            if (bot.getEnergy() <= power + 0.2) return null;
            robocode.Bullet fired = bot.setFireBullet(power);
            if (fired == null) return null;
            bot.shield().register(fired);
            Wave w = new Wave();
            w.origin = new Point2D.Double(me.x, me.y);
            w.fireTime = now;
            w.power = power;
            w.speed = speed;
            w.other = target.name;
            w.directAngle = directAngle;
            w.escapeAngle = escapeAngle;
            w.lateralDirection = target.lateralDirection;
            w.segment = segment;
            w.gunAngles = angles;
            waves.add(w);
            return w;
        }
        private double predictAngle(Point2D.Double me, Enemy target, double speed, boolean circular) {
            double x = target.pos.x, y = target.pos.y;
            double heading = target.heading;
            double turnRate = circular ? Util.clamp(-0.2, target.turnRate, 0.2) : 0.0;
            double velocity = target.velocity;
            java.awt.geom.Rectangle2D.Double f = bot.battleField();
            for (int t = 1; t <= 120; t++) {
                if (Math.hypot(x - me.x, y - me.y) <= speed * t) break;
                heading += turnRate;
                x += Math.sin(heading) * velocity;
                y += Math.cos(heading) * velocity;
                x = Util.clamp(f.getMinX(), x, f.getMaxX());
                y = Util.clamp(f.getMinY(), y, f.getMaxY());
            }
            return Util.angle(me, new Point2D.Double(x, y));
        }
        private double statisticalAngle(Enemy target, double directAngle, double escapeAngle, int segment) {
            float[] stats = target.gfSegments[segment];
            double total = sum(stats);
            if (total < SEGMENT_CONFIDENCE) {
                stats = target.gfGlobal;
                total = sum(stats);
            }
            if (total < GLOBAL_CONFIDENCE) {
                return directAngle;
            }
            int best = CENTER;
            for (int i = 0; i < BINS; i++) {
                if (stats[i] > stats[best]) best = i;
            }
            double gf = (best - CENTER) / (double) CENTER;
            return directAngle + gf * escapeAngle * target.lateralDirection;
        }
        private int chooseGun(Enemy target) {
            int best = LINEAR;
            double bestRate = -1;
            for (int g = 0; g < GUN_COUNT; g++) {
                if (target.virtualShots[g] < MIN_SAMPLES) continue;
                double rate = target.virtualHits[g] / (double) target.virtualShots[g];
                if (rate > bestRate) {
                    bestRate = rate;
                    best = g;
                }
            }
            return best;
        }
        public void update(long now) {
            for (Iterator<Wave> it = waves.iterator(); it.hasNext(); ) {
                Wave w = it.next();
                Enemy target = TCN_bots.KNOWN.get(w.other);
                if (target == null || !target.alive) { it.remove(); continue; }
                Point2D.Double where = target.predictedPosition(now);
                double distance = w.origin.distance(where);
                double radius = w.radius(now);
                if (radius < distance - Util.ROBOT_SIZE / 2) continue;
                if (radius > distance + 60) { it.remove(); continue; }
                record(target, w, where);
                it.remove();
            }
            while (waves.size() > 64) waves.remove(0);
        }
        private void record(Enemy target, Wave w, Point2D.Double where) {
            double gf = w.guessFactor(where);
            int bin = (int) Math.round(gf * CENTER) + CENTER;
            bin = (int) Util.clamp(0, bin, BINS - 1);
            for (int i = 0; i < BINS; i++) {
                float weight = (float) (1.0 / (1.0 + (i - bin) * (i - bin)));
                target.gfSegments[w.segment][i] += weight;
                target.gfGlobal[i] += weight;
            }
            double actual = Util.angle(w.origin, where);
            double hitArc = Math.atan((Util.ROBOT_SIZE / 2) / Math.max(w.origin.distance(where), 40));
            for (int g = 0; g < GUN_COUNT; g++) {
                target.virtualShots[g]++;
                if (Math.abs(Util.relative(w.gunAngles[g] - actual)) < hitArc) {
                    target.virtualHits[g]++;
                }
            }
        }
        private static double sum(float[] a) {
            double t = 0;
            for (int i = 0; i < a.length; i++) t += a[i];
            return t;
        }
        public String activeGunName(Enemy target) {
            if (target == null) return "-";
            switch (chooseGun(target)) {
                case HEAD_ON:  return "direto";
                case LINEAR:   return "linear";
                case CIRCULAR: return "circular";
                default:       return "estatistico";
            }
        }
    }
    static class Movement {
        private static final int ANGLE_STEPS = 32;
        private static final double SWITCH_MARGIN = 0.92;
        private final TCN_bots bot;
        private Point2D.Double destination;
        private double destinationRisk = Double.MAX_VALUE;
        private int    cachedCount;
        private double[] enemyX  = new double[8];
        private double[] enemyY  = new double[8];
        private double[] threat  = new double[8];
        private double[] angleToUs = new double[8];
        private double[] angleToCandidate = new double[8];
        private double[] rawDistance = new double[8];
        private void cacheEnemies(List<Enemy> live, long now) {
            int n = live.size();
            if (enemyX.length < n) {
                enemyX = new double[n]; enemyY = new double[n];
                threat = new double[n]; angleToUs = new double[n];
                angleToCandidate = new double[n]; rawDistance = new double[n];
            }
            cachedCount = n;
            Point2D.Double here = bot.position();
            double bias = bot.profile().survivalBias;
            for (int i = 0; i < n; i++) {
                Enemy e = live.get(i);
                Point2D.Double ep = e.predictedPosition(now);
                enemyX[i] = ep.x;
                enemyY[i] = ep.y;
                threat[i] = (1.0 + Util.clamp(0, e.energy, 150) / 60.0) * bias;
                angleToUs[i] = Util.angle(ep, here);
            }
        }
        public Movement(TCN_bots bot) {
            this.bot = bot;
        }
        public void newRound() {
            destination = null;
            destinationRisk = Double.MAX_VALUE;
        }
        public void driveMelee(List<Enemy> live, long now) {
            Rectangle2D.Double safe = bot.safeField();
            cacheEnemies(live, now);
            if (destination != null) {
                destinationRisk = riskAt(destination);
                if (!safe.contains(destination) || bot.position().distance(destination) < 30) {
                    destination = null;
                    destinationRisk = Double.MAX_VALUE;
                }
            }
            Point2D.Double best = null;
            double bestRisk = Double.MAX_VALUE;
            double[] radii = bot.profile().candidateRadii;
            for (int i = 0; i < ANGLE_STEPS; i++) {
                double angle = i * Util.TWO_PI / ANGLE_STEPS;
                for (int r = 0; r < radii.length; r++) {
                    Point2D.Double candidate = Util.project(bot.position(), angle, radii[r]);
                    if (!safe.contains(candidate)) continue;
                    double risk = riskAt(candidate);
                    if (risk < bestRisk) {
                        bestRisk = risk;
                        best = candidate;
                    }
                }
            }
            if (best != null && (destination == null || bestRisk < destinationRisk * SWITCH_MARGIN)) {
                destination = best;
                destinationRisk = bestRisk;
            }
            if (destination == null) {
                destination = new Point2D.Double(safe.getCenterX(), safe.getCenterY());
            }
            goTo(destination);
        }
        private double riskAt(Point2D candidate) {
            double risk = 0;
            Profile p = bot.profile();
            int n = cachedCount;
            double cx = candidate.getX(), cy = candidate.getY();
            for (int i = 0; i < n; i++) {
                double dx = cx - enemyX[i], dy = cy - enemyY[i];
                rawDistance[i] = Math.sqrt(dx * dx + dy * dy);
                angleToCandidate[i] = Math.atan2(dx, dy);
            }
            for (int i = 0; i < n; i++) {
                double d = Math.max(rawDistance[i], 25);
                risk += threat[i] * 12000.0 / (d * d);
                double delta = angleToCandidate[i] - angleToUs[i];
                risk += threat[i] * 90.0 * Math.abs(Math.cos(delta)) / Math.sqrt(d);
                for (int j = i + 1; j < n; j++) {
                    double between = Math.abs(Util.relative(angleToCandidate[i] - angleToCandidate[j]));
                    if (between > 2.6) {
                        risk += 260.0 / Math.max(rawDistance[i], 60);
                    }
                }
            }
            Rectangle2D.Double f = bot.safeField();
            double wallGap = Math.min(
                    Math.min(candidate.getX() - f.getMinX(), f.getMaxX() - candidate.getX()),
                    Math.min(candidate.getY() - f.getMinY(), f.getMaxY() - candidate.getY()));
            if (wallGap < p.wallGap) risk += (p.wallGap - wallGap) * 1.6;
            double cornerGap = Math.min(
                    Math.min(dist(candidate, f.getMinX(), f.getMinY()), dist(candidate, f.getMinX(), f.getMaxY())),
                    Math.min(dist(candidate, f.getMaxX(), f.getMinY()), dist(candidate, f.getMaxX(), f.getMaxY())));
            if (cornerGap < p.cornerGap) risk += (p.cornerGap - cornerGap) * 2.2;
            risk += bot.position().distance(candidate) * 0.05;
            return risk;
        }
        private static double dist(Point2D p, double x, double y) {
            return Math.hypot(p.getX() - x, p.getY() - y);
        }
        public void driveDuel(Enemy target, long now) {
            if (target == null) {
                goTo(new Point2D.Double(bot.safeField().getCenterX(), bot.safeField().getCenterY()));
                return;
            }
            double desired = target.absBearing + Util.HALF_PI * duelDirection;
            double correction = (target.distance - 420) / 500.0;
            desired -= correction * duelDirection;
            Point2D.Double ahead = Util.project(bot.position(), desired, 150);
            if (!bot.safeField().contains(ahead)) {
                duelDirection = -duelDirection;
                desired = target.absBearing + Util.HALF_PI * duelDirection;
            }
            if (target.justFiredPower > 0 && now - lastFlip > 4) {
                duelDirection = -duelDirection;
                lastFlip = now;
            }
            double turn = Util.relative(desired - bot.getHeadingRadians());
            int direction = 1;
            if (Math.abs(turn) > Util.HALF_PI) {
                turn = Util.relative(turn + Math.PI);
                direction = -1;
            }
            bot.setTurnRightRadians(turn);
            bot.setAhead(direction * 120);
            bot.setMaxVelocity(Util.MAX_VELOCITY);
        }
        private int  duelDirection = 1;
        private long lastFlip = -99;
        private void goTo(Point2D dest) {
            double angle = Util.relative(Util.angle(bot.position(), dest) - bot.getHeadingRadians());
            double distance = bot.position().distance(dest);
            int direction = 1;
            if (Math.abs(angle) > Util.HALF_PI) {
                angle = Util.relative(angle + Math.PI);
                direction = -1;
            }
            bot.setTurnRightRadians(angle);
            bot.setAhead(distance * direction);
            bot.setMaxVelocity(Math.abs(angle) > 1.2 ? 5.0 : Util.MAX_VELOCITY);
        }
    }
    static class Profile {
        private static final double REFERENCE = 1000.0;
        public final double scale;
        public final double[] candidateRadii;
        public final double wallGap;
        public final double cornerGap;
        public final double safeInset;
        public final double orbitDistance;
        public final double gunNear;
        public final double gunFar;
        public final double pointBlank;
        public final double survivalBias;
        public final double firePowerScale;
        private Profile(double width, double height, double survivalBias, double firePowerScale) {
            this.scale = Util.clamp(0.55, Math.sqrt(width * height) / REFERENCE, 1.7);
            this.candidateRadii = new double[] { 100 * scale, 160 * scale, 230 * scale };
            this.wallGap    = 90  * scale;
            this.cornerGap  = 220 * scale;
            this.safeInset  = Math.max(25, 45 * scale);
            this.orbitDistance = 700 * scale;
            this.gunNear    = 250 * scale;
            this.gunFar     = 550 * scale;
            this.pointBlank = 200 * scale;
            this.survivalBias   = survivalBias;
            this.firePowerScale = firePowerScale;
        }
        public static Profile forBattle(double width, double height, int enemies) {
            double survival;
            double power;
            if (enemies >= 2) {
                survival = 0.7;
                power    = 1.9;
            } else {
                survival = 1.0;
                power    = 0.8;
            }
            return new Profile(width, height, survival, power);
        }
    }
    static class Shield {
        private static final double PARRY_POWER = 0.1;
        private static final double WORTH_PARRYING = 1.2;
        private static final double TIMING_SLACK = 0.55;
        private final TCN_bots bot;
        private final List<Bullet> mine = new ArrayList<Bullet>();
        private int parryAttempts;
        private int parrySuccesses;
        public Shield(TCN_bots bot) {
            this.bot = bot;
        }
        public void newRound() {
            mine.clear();
        }
        public void register(Bullet b) {
            if (b != null) mine.add(b);
        }
        public void update() {
            for (Iterator<Bullet> it = mine.iterator(); it.hasNext(); ) {
                if (!it.next().isActive()) it.remove();
            }
        }
        public void onParrySuccess() {
            parrySuccesses++;
        }
        public String stats() {
            return parrySuccesses + "/" + parryAttempts;
        }
        public List<double[]> shadows(Wave wave, long now) {
            List<double[]> arcs = new ArrayList<double[]>();
            for (int i = 0; i < mine.size(); i++) {
                Bullet b = mine.get(i);
                if (!b.isActive()) continue;
                double x = b.getX(), y = b.getY();
                double heading = b.getHeadingRadians();
                double speed = b.getVelocity();
                double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
                boolean touching = false;
                for (int t = 0; t <= 60; t++) {
                    double px = x + Math.sin(heading) * speed * t;
                    double py = y + Math.cos(heading) * speed * t;
                    if (!bot.battleField().contains(px, py)) break;
                    double gap = Math.hypot(px - wave.origin.x, py - wave.origin.y)
                               - wave.radius(now + t);
                    if (Math.abs(gap) <= (wave.speed + speed) / 2.0) {
                        double offset = Util.relative(
                                Util.angle(wave.origin, new Point2D.Double(px, py)) - wave.directAngle);
                        min = Math.min(min, offset);
                        max = Math.max(max, offset);
                        touching = true;
                    } else if (touching) {
                        break;
                    }
                }
                if (touching) {
                    double pad = Math.atan(6.0 / Math.max(
                            wave.origin.distance(new Point2D.Double(x, y)), 60));
                    arcs.add(new double[] { min - pad, max + pad });
                }
            }
            return arcs;
        }
        public static boolean covered(List<double[]> arcs, double offset) {
            for (int i = 0; i < arcs.size(); i++) {
                if (offset >= arcs.get(i)[0] && offset <= arcs.get(i)[1]) return true;
            }
            return false;
        }
        public boolean tryParry(Wave wave, Enemy shooter, long now) {
            if (wave == null || shooter == null) return false;
            if (wave.power < WORTH_PARRYING) return false;
            if (bot.getGunHeat() > 0) return false;
            if (bot.getEnergy() < 3.0) return false;
            double bulletAngle = wave.angleFor(favouriteGuessFactor(shooter));
            double ourSpeed = Util.bulletSpeed(PARRY_POWER);
            Point2D.Double me = bot.position();
            for (int t = 2; t <= 28; t++) {
                long when = now + t;
                double travelled = wave.speed * (when - wave.fireTime);
                Point2D.Double p = Util.project(wave.origin, bulletAngle, travelled);
                if (!bot.battleField().contains(p)) return false;
                double needed = me.distance(p) / ourSpeed;
                if (Math.abs(needed - t) > TIMING_SLACK) continue;
                double turn = Util.relative(Util.angle(me, p) - bot.getGunHeadingRadians());
                if (Math.abs(turn) > 0.015) {
                    bot.setTurnGunRightRadians(turn);
                    return false;
                }
                if (bot.setFireBullet(PARRY_POWER) != null) {
                    parryAttempts++;
                    return true;
                }
                return false;
            }
            return false;
        }
        private double favouriteGuessFactor(Enemy shooter) {
            int best = Gun.CENTER;
            float bestValue = 0;
            for (int i = 0; i < Gun.BINS; i++) {
                if (shooter.surfGlobal[i] > bestValue) {
                    bestValue = shooter.surfGlobal[i];
                    best = i;
                }
            }
            return bestValue <= 0 ? 0.0 : (best - Gun.CENTER) / (double) Gun.CENTER;
        }
    }
    static class Surf {
        private static final int MAX_SIM_TICKS = 220;
        private static final double SEGMENT_CONFIDENCE = 4.0;
        private static final int WAVES_CONSIDERED = 2;
        private static final double NEXT_WAVE_WEIGHT = 0.20;
        private int segmentOf(double distance, double lateralVelocity) {
            Profile p = bot.profile();
            int d = distance < p.gunNear ? 0 : (distance < p.gunFar ? 1 : 2);
            double lateral = Math.abs(lateralVelocity);
            int v = lateral < 2.0 ? 0 : (lateral < 5.5 ? 1 : 2);
            return d * 3 + v;
        }
        private static double sum(float[] a) {
            double t = 0;
            for (int i = 0; i < a.length; i++) t += a[i];
            return t;
        }
        private final TCN_bots bot;
        private final List<Wave> incoming = new ArrayList<Wave>();
        public Surf(TCN_bots bot) {
            this.bot = bot;
        }
        public void newRound() {
            incoming.clear();
        }
        public void onEnemyFire(Enemy e) {
            if (e.justFiredPower <= 0) return;
            Wave w = new Wave();
            w.origin = new Point2D.Double(e.previousPos.x, e.previousPos.y);
            w.fireTime = e.lastSeen - 1;
            w.power = e.justFiredPower;
            w.speed = Util.bulletSpeed(e.justFiredPower);
            w.other = e.name;
            w.directAngle = Util.angle(w.origin, bot.position());
            w.escapeAngle = Util.maxEscapeAngle(w.speed);
            double lateral = bot.getVelocity()
                    * Math.sin(bot.getHeadingRadians() - w.directAngle);
            w.lateralDirection = Util.sign(lateral == 0 ? 1 : lateral);
            w.priorGuessFactor = Math.abs(lateral) / Util.MAX_VELOCITY;
            w.segment = segmentOf(bot.position().distance(w.origin), lateral);
            incoming.add(w);
        }
        public void update(long now) {
            for (Iterator<Wave> it = incoming.iterator(); it.hasNext(); ) {
                Wave w = it.next();
                if (w.radius(now) > bot.position().distance(w.origin) + 50) {
                    it.remove();
                }
            }
            while (incoming.size() > 12) incoming.remove(0);
        }
        public Wave closestWave(long now) {
            List<Wave> ordered = wavesByImminence(now, 1);
            return ordered.isEmpty() ? null : ordered.get(0);
        }
        private List<Wave> wavesByImminence(long now, int limit) {
            List<Wave> out = new ArrayList<Wave>();
            List<Double> gaps = new ArrayList<Double>();
            Point2D.Double me = bot.position();
            for (int i = 0; i < incoming.size(); i++) {
                Wave w = incoming.get(i);
                double gap = me.distance(w.origin) - w.radius(now);
                if (gap <= -10) continue;
                int at = 0;
                while (at < gaps.size() && gaps.get(at) < gap) at++;
                gaps.add(at, gap);
                out.add(at, w);
            }
            while (out.size() > limit) out.remove(out.size() - 1);
            return out;
        }
        public void onHitByBullet(HitByBulletEvent event, Enemy shooter, long now) {
            if (shooter == null) return;
            Point2D.Double hitAt = bot.position();
            Wave matched = null;
            double bestError = Double.MAX_VALUE;
            for (int i = 0; i < incoming.size(); i++) {
                Wave w = incoming.get(i);
                if (!w.other.equals(shooter.name)) continue;
                double error = Math.abs(w.radius(now) - hitAt.distance(w.origin))
                             + Math.abs(w.power - event.getPower()) * 40;
                if (error < bestError) {
                    bestError = error;
                    matched = w;
                }
            }
            if (matched == null || bestError > 60) return;
            int bin = binOf(matched.guessFactor(hitAt));
            for (int i = 0; i < Gun.BINS; i++) {
                float weight = (float) (1.0 / (1.0 + (i - bin) * (i - bin)));
                shooter.surfSegments[matched.segment][i] += weight;
                shooter.surfGlobal[i] += weight;
            }
            incoming.remove(matched);
        }
        public void drive(Enemy target, long now) {
            List<Wave> waves = wavesByImminence(now, WAVES_CONSIDERED);
            if (waves.isEmpty() || target == null) {
                orbit(target, now);
                return;
            }
            Wave wave = waves.get(0);
            List<double[]> shadows = TCN_bots.USE_BULLET_SHADOW
                    ? bot.shield().shadows(wave, now)
                    : java.util.Collections.<double[]>emptyList();
            double dangerLeft = 0, dangerRight = 0, weight = 1.0;
            for (int i = 0; i < waves.size(); i++) {
                Wave w = waves.get(i);
                List<double[]> s = (i == 0) ? shadows : java.util.Collections.<double[]>emptyList();
                dangerLeft  += weight * dangerOf(w, -1, now, target, s);
                dangerRight += weight * dangerOf(w, +1, now, target, s);
                weight *= NEXT_WAVE_WEIGHT;
            }
            int direction = dangerLeft < dangerRight ? -1 : +1;
            steerAround(wave.origin, direction);
        }
        private double dangerOf(Wave wave, int direction, long now, Enemy target,
                                List<double[]> shadows) {
            Point2D.Double landing = simulate(wave, direction, now);
            double offset = Util.relative(Util.angle(wave.origin, landing) - wave.directAngle);
            if (Shield.covered(shadows, offset)) {
                return 30.0 / Math.max(landing.distance(wave.origin), 60);
            }
            int bin = binOf(wave.guessFactor(landing));
            float[] stats = target.surfSegments[wave.segment];
            double total = sum(stats);
            if (total < SEGMENT_CONFIDENCE) {
                stats = target.surfGlobal;
                total = sum(stats);
            }
            double learned = total > 0 ? stats[bin] / total : 0;
            double confidence = Util.clamp(0, total / 15.0, 1.0);
            double danger = confidence * learned + (1 - confidence) * priorDanger(wave, bin);
            danger += 30.0 / Math.max(landing.distance(wave.origin), 60);
            return danger;
        }
        private double priorDanger(Wave wave, int bin) {
            int linearBin = binOf(wave.priorGuessFactor);
            double dLinear = bin - linearBin;
            double dHeadOn = bin - Gun.CENTER;
            return 1.0 / (1.0 + dLinear * dLinear * 0.5)
                 + 0.6 / (1.0 + dHeadOn * dHeadOn * 0.5);
        }
        private static int binOf(double guessFactor) {
            return (int) Util.clamp(0, Math.round(guessFactor * Gun.CENTER) + Gun.CENTER, Gun.BINS - 1);
        }
        private Point2D.Double simulate(Wave wave, int direction, long now) {
            Point2D.Double p = new Point2D.Double(bot.getX(), bot.getY());
            double heading  = bot.getHeadingRadians();
            double velocity = bot.getVelocity();
            for (int tick = 1; tick <= MAX_SIM_TICKS; tick++) {
                double desired = orbitAngle(p, wave.origin, direction);
                double turn = Util.relative(desired - heading);
                double drive = 1;
                if (Math.cos(turn) < 0) {
                    turn = Util.relative(turn + Math.PI);
                    drive = -1;
                }
                double maxTurn = Math.PI / 720.0 * (40.0 - 3.0 * Math.abs(velocity));
                heading = Util.relative(heading + Util.clamp(-maxTurn, turn, maxTurn));
                velocity += (velocity * drive < 0) ? 2.0 * drive : drive;
                velocity = Util.clamp(-Util.MAX_VELOCITY, velocity, Util.MAX_VELOCITY);
                p = Util.project(p, heading, velocity);
                if (p.distance(wave.origin) <= wave.radius(now + tick) + wave.speed) {
                    break;
                }
            }
            return p;
        }
        private double smoothAgainstWalls(Point2D.Double from, double angle, int direction) {
            Rectangle2D.Double safe = bot.safeField();
            double stick = 150;
            int guard = 0;
            while (!safe.contains(Util.project(from, angle, stick)) && guard++ < 100) {
                angle += direction * 0.05;
            }
            return angle;
        }
        private double orbitAngle(Point2D.Double from, Point2D.Double center, int direction) {
            double radialOut = Util.angle(center, from);
            double correction = Util.clamp(-0.6,
                    (bot.profile().orbitDistance - from.distance(center)) / 400.0, 0.6);
            return smoothAgainstWalls(from,
                    radialOut + Util.HALF_PI * direction - correction * direction, direction);
        }
        private void steerAround(Point2D.Double center, int direction) {
            double desired = orbitAngle(bot.position(), center, direction);
            double turn = Util.relative(desired - bot.getHeadingRadians());
            int drive = 1;
            if (Math.cos(turn) < 0) {
                turn = Util.relative(turn + Math.PI);
                drive = -1;
            }
            bot.setTurnRightRadians(turn);
            bot.setAhead(drive * 100);
            bot.setMaxVelocity(Util.MAX_VELOCITY);
        }
        private void orbit(Enemy target, long now) {
            if (target == null) {
                Rectangle2D.Double safe = bot.safeField();
                steerAround(new Point2D.Double(safe.getCenterX(), safe.getCenterY()), 1);
                return;
            }
            double correction = Util.clamp(-0.7,
                    (target.distance - bot.profile().orbitDistance) / 400.0, 0.7);
            double desired = smoothAgainstWalls(bot.position(),
                    target.absBearing + Util.HALF_PI * idleDirection - correction * idleDirection,
                    idleDirection);
            double turn = Util.relative(desired - bot.getHeadingRadians());
            int drive = 1;
            if (Math.cos(turn) < 0) {
                turn = Util.relative(turn + Math.PI);
                drive = -1;
            }
            bot.setTurnRightRadians(turn);
            bot.setAhead(drive * 100);
            bot.setMaxVelocity(Util.MAX_VELOCITY);
            if (now - lastIdleFlip > 40) {
                idleDirection = -idleDirection;
                lastIdleFlip = now;
            }
        }
        private int  idleDirection = 1;
        private long lastIdleFlip = 0;
    }
    static final class Util {
        public static final double TWO_PI  = Math.PI * 2;
        public static final double HALF_PI = Math.PI / 2;
        public static final double MAX_VELOCITY = 8.0;
        public static final double ROBOT_SIZE   = 36.0;
        private Util() {}
        public static double relative(double angle) {
            double a = angle % TWO_PI;
            if (a >= Math.PI)  a -= TWO_PI;
            if (a < -Math.PI)  a += TWO_PI;
            return a;
        }
        public static double absolute(double angle) {
            double a = angle % TWO_PI;
            return a < 0 ? a + TWO_PI : a;
        }
        public static double angle(Point2D from, Point2D to) {
            return Math.atan2(to.getX() - from.getX(), to.getY() - from.getY());
        }
        public static Point2D.Double project(Point2D from, double angle, double distance) {
            return new Point2D.Double(from.getX() + Math.sin(angle) * distance,
                                      from.getY() + Math.cos(angle) * distance);
        }
        public static double clamp(double min, double value, double max) {
            return value < min ? min : (value > max ? max : value);
        }
        public static double bulletSpeed(double power) {
            return 20.0 - 3.0 * power;
        }
        public static double bulletDamage(double power) {
            return power <= 1.0 ? 4.0 * power : 4.0 * power + 2.0 * (power - 1.0);
        }
        public static double bulletReward(double power) {
            return 3.0 * power;
        }
        public static double gunHeat(double power) {
            return 1.0 + power / 5.0;
        }
        public static double maxEscapeAngle(double bulletSpeed) {
            return Math.asin(MAX_VELOCITY / bulletSpeed);
        }
        public static int sign(double v) {
            return v < 0 ? -1 : 1;
        }
    }
    static class Wave {
        public Point2D.Double origin;
        public long   fireTime;
        public double power;
        public double speed;
        public String other;
        public double directAngle;
        public double escapeAngle;
        public int lateralDirection;
        public int segment;
        public double priorGuessFactor;
        public double[] gunAngles;
        public boolean processed;
        public double radius(long now) {
            return (now - fireTime) * speed;
        }
        public double guessFactor(Point2D point) {
            double offset = Util.relative(Util.angle(origin, point) - directAngle);
            return Util.clamp(-1, offset / escapeAngle * lateralDirection, 1);
        }
        public double angleFor(double guessFactor) {
            return directAngle + guessFactor * escapeAngle * lateralDirection;
        }
    }
}
