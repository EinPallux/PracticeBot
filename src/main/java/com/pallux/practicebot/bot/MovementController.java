package com.pallux.practicebot.bot;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Professional movement controller with custom A* pathfinding
 * No external dependencies - pure Bukkit API
 */
public class MovementController {

    private final Player bot;
    private List<Location> currentPath;
    private int currentPathIndex;
    private boolean isMoving;
    private Location lastTarget;
    private int stuckTicks;
    private Location lastLocation;

    // Movement constants - real player values
    private static final double WALK_SPEED = 0.2;
    private static final double SPRINT_SPEED = 0.26;
    private static final double JUMP_VELOCITY = 0.42;
    private static final int MAX_PATH_LENGTH = 100;

    public MovementController(Player bot) {
        this.bot = bot;
        this.currentPathIndex = 0;
        this.isMoving = false;
        this.stuckTicks = 0;
        this.lastLocation = bot.getLocation();
    }

    /**
     * Move to location using custom A* pathfinding
     */
    public void moveTo(Location target, boolean sprint) {
        if (target == null) return;

        // Don't recalculate if target is close and we're already moving
        if (lastTarget != null && lastTarget.distance(target) < 3.0 && isMoving && currentPath != null) {
            return;
        }

        lastTarget = target.clone();

        // Calculate path using A*
        currentPath = findPath(bot.getLocation(), target);

        if (currentPath != null && !currentPath.isEmpty()) {
            currentPathIndex = 0;
            isMoving = true;
            stuckTicks = 0;
        } else {
            // No path found - try direct movement
            isMoving = true;
        }
    }

    /**
     * A* Pathfinding Algorithm
     */
    private List<Location> findPath(Location start, Location goal) {
        if (start.getWorld() != goal.getWorld()) return null;

        // Simplify locations to block coordinates
        Location startBlock = start.getBlock().getLocation();
        Location goalBlock = goal.getBlock().getLocation();

        // If too far, return null
        if (startBlock.distance(goalBlock) > MAX_PATH_LENGTH) {
            return null;
        }

        // A* data structures
        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Set<Location> closedSet = new HashSet<>();
        Map<Location, Location> cameFrom = new HashMap<>();
        Map<Location, Double> gScore = new HashMap<>();

        // Initialize
        gScore.put(startBlock, 0.0);
        openSet.add(new Node(startBlock, 0, heuristic(startBlock, goalBlock)));

        int iterations = 0;
        int maxIterations = 1000;

        while (!openSet.isEmpty() && iterations < maxIterations) {
            iterations++;

            Node current = openSet.poll();
            Location currentLoc = current.location;

            // Goal reached
            if (currentLoc.distance(goalBlock) < 2.0) {
                return reconstructPath(cameFrom, currentLoc);
            }

            closedSet.add(currentLoc);

            // Check neighbors
            for (Location neighbor : getNeighbors(currentLoc)) {
                if (closedSet.contains(neighbor)) continue;

                double tentativeGScore = gScore.getOrDefault(currentLoc, Double.MAX_VALUE) + 1.0;

                if (tentativeGScore < gScore.getOrDefault(neighbor, Double.MAX_VALUE)) {
                    cameFrom.put(neighbor, currentLoc);
                    gScore.put(neighbor, tentativeGScore);
                    double fScore = tentativeGScore + heuristic(neighbor, goalBlock);
                    openSet.add(new Node(neighbor, tentativeGScore, fScore));
                }
            }
        }

        // No path found
        return null;
    }

    /**
     * Get walkable neighbors
     */
    private List<Location> getNeighbors(Location loc) {
        List<Location> neighbors = new ArrayList<>();

        int[][] directions = {
                {1, 0, 0}, {-1, 0, 0},
                {0, 0, 1}, {0, 0, -1},
                {1, 0, 1}, {-1, 0, -1},
                {1, 0, -1}, {-1, 0, 1}
        };

        for (int[] dir : directions) {
            Location neighbor = loc.clone().add(dir[0], dir[1], dir[2]);

            if (isWalkable(neighbor)) {
                neighbors.add(neighbor);
            }
        }

        return neighbors;
    }

    /**
     * Check if location is walkable
     */
    private boolean isWalkable(Location loc) {
        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        Block ground = loc.clone().add(0, -1, 0).getBlock();

        // Check if we can stand here
        if (feet.getType().isSolid() || head.getType().isSolid()) {
            // Check if we can jump up
            Block aboveHead = loc.clone().add(0, 2, 0).getBlock();
            if (!aboveHead.getType().isSolid() && feet.getType().isSolid()) {
                return true; // Can jump
            }
            return false;
        }

        // Need solid ground
        if (!ground.getType().isSolid()) {
            // Check one block down
            Block groundBelow = loc.clone().add(0, -2, 0).getBlock();
            if (groundBelow.getType().isSolid()) {
                return true; // Can walk down
            }
            return false;
        }

        return true;
    }

    /**
     * Heuristic for A* (Manhattan distance)
     */
    private double heuristic(Location a, Location b) {
        return Math.abs(a.getX() - b.getX()) +
                Math.abs(a.getY() - b.getY()) +
                Math.abs(a.getZ() - b.getZ());
    }

    /**
     * Reconstruct path from A* result
     */
    private List<Location> reconstructPath(Map<Location, Location> cameFrom, Location current) {
        List<Location> path = new ArrayList<>();
        path.add(current);

        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            path.add(0, current);
        }

        return path;
    }

    /**
     * Tick movement - call every tick
     */
    public void tick(boolean sprint) {
        if (!isMoving) return;

        // Check if stuck
        if (bot.getLocation().distance(lastLocation) < 0.05) {
            stuckTicks++;
            if (stuckTicks > 40) { // Stuck for 2 seconds
                // Recalculate path
                if (lastTarget != null) {
                    currentPath = findPath(bot.getLocation(), lastTarget);
                    currentPathIndex = 0;
                    stuckTicks = 0;
                }
            }
        } else {
            stuckTicks = 0;
        }
        lastLocation = bot.getLocation().clone();

        // Follow path if available
        if (currentPath != null && !currentPath.isEmpty()) {
            followPath(sprint);
        } else if (lastTarget != null) {
            // Direct movement as fallback
            moveDirectly(lastTarget, sprint);
        }
    }

    /**
     * Follow calculated path
     */
    private void followPath(boolean sprint) {
        if (currentPathIndex >= currentPath.size()) {
            isMoving = false;
            stop();
            return;
        }

        Location targetWaypoint = currentPath.get(currentPathIndex);
        double distance = bot.getLocation().distance(targetWaypoint);

        if (distance < 0.8) {
            currentPathIndex++;
            if (currentPathIndex >= currentPath.size()) {
                isMoving = false;
                stop();
                return;
            }
            targetWaypoint = currentPath.get(currentPathIndex);
        }

        moveTowardsPoint(targetWaypoint, sprint);
    }

    /**
     * Move directly to target (fallback)
     */
    private void moveDirectly(Location target, boolean sprint) {
        double distance = bot.getLocation().distance(target);
        if (distance < 0.5) {
            isMoving = false;
            stop();
            return;
        }

        moveTowardsPoint(target, sprint);
    }

    /**
     * Move towards a specific point
     */
    private void moveTowardsPoint(Location target, boolean sprint) {
        Location botLoc = bot.getLocation();
        Vector direction = target.toVector().subtract(botLoc.toVector());
        direction.setY(0);

        double distance = direction.length();
        if (distance < 0.1) return;

        direction.normalize();

        // Set speed
        double speed = sprint ? SPRINT_SPEED : WALK_SPEED;
        bot.setSprinting(sprint);

        // Jump if needed
        if (shouldJump(target)) {
            jump();
        }

        // Apply velocity
        Vector velocity = direction.multiply(speed);
        velocity.setY(bot.getVelocity().getY());
        bot.setVelocity(velocity);

        // Look at target
        lookAt(target);
    }

    /**
     * Strafe movement
     */
    public void strafe(Location lookTarget, int direction, boolean sprint) {
        Vector toLook = lookTarget.toVector().subtract(bot.getLocation().toVector());
        toLook.setY(0);
        toLook.normalize();

        Vector strafeDir = new Vector(-toLook.getZ(), 0, toLook.getX());
        strafeDir.multiply(direction);

        double speed = (sprint ? SPRINT_SPEED : WALK_SPEED) * 0.85;
        bot.setSprinting(sprint);

        Vector velocity = strafeDir.multiply(speed);
        velocity.setY(bot.getVelocity().getY());
        bot.setVelocity(velocity);

        lookAt(lookTarget);
    }

    /**
     * Check if should jump
     */
    private boolean shouldJump(Location target) {
        Location botLoc = bot.getLocation();
        Vector direction = target.toVector().subtract(botLoc.toVector());
        direction.setY(0);
        direction.normalize();

        Location checkLoc = botLoc.clone().add(direction.multiply(0.5));

        if (checkLoc.getBlock().getType().isSolid() && bot.isOnGround()) {
            Location above = checkLoc.clone().add(0, 1, 0);
            if (!above.getBlock().getType().isSolid()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Jump
     */
    public void jump() {
        if (!bot.isOnGround()) return;

        Vector velocity = bot.getVelocity();
        velocity.setY(JUMP_VELOCITY);
        bot.setVelocity(velocity);
    }

    /**
     * Look at location smoothly
     */
    public void lookAt(Location target) {
        Vector direction = target.toVector().subtract(bot.getLocation().toVector());
        Location lookLoc = bot.getLocation().clone();
        lookLoc.setDirection(direction);

        float currentYaw = bot.getLocation().getYaw();
        float currentPitch = bot.getLocation().getPitch();
        float targetYaw = lookLoc.getYaw();
        float targetPitch = lookLoc.getPitch();

        float smoothFactor = 0.4f;
        float newYaw = currentYaw + angleDifference(targetYaw, currentYaw) * smoothFactor;
        float newPitch = currentPitch + (targetPitch - currentPitch) * smoothFactor;

        lookLoc.setYaw(newYaw);
        lookLoc.setPitch(newPitch);

        bot.teleport(lookLoc);
    }

    /**
     * Calculate angle difference
     */
    private float angleDifference(float target, float current) {
        float diff = target - current;
        while (diff < -180) diff += 360;
        while (diff > 180) diff -= 360;
        return diff;
    }

    /**
     * Stop movement
     */
    public void stop() {
        Vector velocity = bot.getVelocity();
        velocity.setX(0);
        velocity.setZ(0);
        bot.setVelocity(velocity);
        bot.setSprinting(false);
        isMoving = false;
        currentPath = null;
        lastTarget = null;
    }

    public boolean isMoving() {
        return isMoving;
    }

    public boolean isStuck() {
        return stuckTicks > 40;
    }

    public double getDistanceToTarget() {
        if (lastTarget == null) return 0;
        return bot.getLocation().distance(lastTarget);
    }

    public void cancelPath() {
        isMoving = false;
        currentPath = null;
        currentPathIndex = 0;
        lastTarget = null;
    }

    /**
     * Node class for A* pathfinding
     */
    private static class Node implements Comparable<Node> {
        Location location;
        double gScore;
        double fScore;

        Node(Location location, double gScore, double fScore) {
            this.location = location;
            this.gScore = gScore;
            this.fScore = fScore;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.fScore, other.fScore);
        }
    }
}