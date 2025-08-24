package org.ieathex.antiBoatFly;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

public final class AntiBoatFly extends JavaPlugin implements Listener {

    private static final double watermax = 0.9;
    private static final double icemax = 4;
    private static final double landmax = 0.3;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("AntiBoatFly v1.0.0 - made with \u001B[31m<3\u001B[0m from \u001B[1m\u001B[34mIEatHex\u001B[0m");
        getLogger().info("Please report any bugs or issues to: https://github.com/DigitalSerpant/AntiBoatFly");
    }

    private void sendback(VehicleMoveEvent event) {
        Boat boat = (Boat) event.getVehicle();
        Location destination = event.getFrom();

        if (!boat.getPassengers().isEmpty() && boat.getPassengers().get(0) instanceof Player) {
            for (Entity passenger : boat.getPassengers()) {
                if (passenger instanceof Player) {
                    Player player = (Player) passenger;
                    player.teleport(destination);
                    // we send each player back one by one to where they started to prevent remounting movement exploits
                }
            }
        }
        boat.teleport(event.getFrom());
    }


    public static double getPlauseableDecimal(double coordinate) {
        String coordStr = String.format("%.17f", Math.abs(coordinate));
        String suffix = coordStr.substring(coordStr.indexOf("0625") + 4);
        //https://github.com/AntiCope/meteor-rejects/blob/4563a7e7ef55ef903b56c5fd290e88c9e16b3f48/src/main/java/anticope/rejects/modules/PacketFly.java#L190
        suffix = suffix.replaceAll("0+$", "");
        if (suffix.endsWith(".")) {
            suffix = suffix.substring(0, suffix.length() - 1);
        }
        return Double.parseDouble(suffix);
    }

    boolean isNoEventPos(double coordinate) {
        return String.format("%.5f", Math.abs(coordinate)).contains(".0625");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Boat)) {
            return;
        }
        try {
            Boat boat = (Boat) event.getVehicle();

            if (isNoEventPos(event.getTo().getX()) && isNoEventPos(event.getTo().getY()) && isNoEventPos(event.getTo().getZ())) { 
                // decimal value used to trigger the exploit. we will catch it here and send them back.
                double newX = getPlauseableDecimal(event.getTo().getX());
                double newY = getPlauseableDecimal(event.getTo().getY());
                double newZ = getPlauseableDecimal(event.getTo().getZ());
                // https://github.com/AntiCope/meteor-rejects/blob/4563a7e7ef55ef903b56c5fd290e88c9e16b3f48/src/main/java/anticope/rejects/modules/PacketFly.java#L172

                newX *= Math.signum(event.getFrom().getX());
                newY *= Math.signum(event.getFrom().getY());
                newZ *= Math.signum(event.getFrom().getZ());

                sendback(new VehicleMoveEvent(boat, new Location(event.getTo().getWorld(), newX, newY, newZ), event.getFrom())); //send them back without gaining any +y or other directional movement
                return;
            }    // great video on it here: https://www.youtube.com/watch?v=RDkWagIW6gw


            double maxspeed = getMaxSpeedForTerrain(boat); // allows us to get the max speed allowed per terrain

            if (event.getTo().getY() - event.getFrom().getY() < 0 && event.getTo().distance(event.getFrom()) < icemax) {
                return;
            }   // we will move on if the are going downwards and the speed going down is less than the possible speed in minecraft for a boat.

            if (event.getTo().distance(event.getFrom()) > maxspeed) {
                sendback(event);
                return; // Per terrain filtering.
            }

            if (goingUp(event) && !isBoatSupported(boat)) {
                sendback(event);
                return; // Kick them from the boat if they are trying to go up.
            }

            if (!isBoatSupported(boat)) {
                sendback(event);
                return; // if they are not being supported they way boats normally should send them back.
            }

            if (boat.getPassengers().isEmpty()) {
                return;
            }

            if (!(boat.getPassengers().get(0) instanceof Player)) {
                return; // just in case
            }
        } catch (Exception e) {} // stay safe kids </3
    }

    private boolean goingUp(VehicleMoveEvent event) {
        return event.getTo().getY() - event.getFrom().getY() > 0;
    }

    private double getMaxSpeedForTerrain(Boat boat) {
        Location boatLoc = boat.getLocation();
        terrain terrain = getTType(boat, boatLoc);

        switch (terrain) {
            case water:
                return watermax;
            case ice:
                return icemax;
            case land:
                return landmax;
            default:
                return landmax;
        }
    }

    private terrain getTType(Boat boat, Location boatLoc) {// this lets us get the current terrain the boat is on
        if (boatLoc.getBlock().getType() == Material.WATER) {
            return terrain.water;
        }

        BoundingBox boatBounds = boat.getBoundingBox();
        double correction = 0.3;

        BoundingBox checkBounds = new BoundingBox(
                boatBounds.getMinX() - correction,
                boatBounds.getMinY() - correction,
                boatBounds.getMinZ() - correction,
                boatBounds.getMaxX() + correction,
                boatBounds.getMinY(),
                boatBounds.getMaxZ() + correction);

        int minX = (int) Math.floor(checkBounds.getMinX());
        int maxX = (int) Math.floor(checkBounds.getMaxX());
        int minY = (int) Math.floor(checkBounds.getMinY());
        int maxY = (int) Math.floor(checkBounds.getMaxY());
        int minZ = (int) Math.floor(checkBounds.getMinZ());
        int maxZ = (int) Math.floor(checkBounds.getMaxZ());
        boolean hasWater = false;
        boolean hasIce = false;
        boolean hasSolid = false;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = boatLoc.getWorld().getBlockAt(x, y, z);

                    if (block.getType() == Material.AIR) {
                        continue;
                    }

                    if (block.getType() == Material.WATER) {
                        hasWater = true;
                    } else if (isIceBlock(block.getType())) {
                        hasIce = true;
                    } else if (block.getType().isSolid()) {
                        hasSolid = true;
                    }
                }
            }
        }

        if (hasWater) {
            return terrain.water;
        } else if (hasIce) {
            return terrain.ice;
        } else if (hasSolid) {
            return terrain.land;
        }

        return terrain.land;
    }

    private boolean isIceBlock(Material material) {
        return material == Material.ICE ||
                material == Material.PACKED_ICE ||
                material == Material.BLUE_ICE ||
                material == Material.FROSTED_ICE;
                // cold
    }

    private boolean isBoatSupported(Boat boat) { // this will allow 
        Location boatLoc = boat.getLocation();
        
        if (boatLoc.getBlock().getType() == Material.WATER) {
            return true;
        }

        BoundingBox boatBounds = boat.getBoundingBox();

        double correction = 0.3; // slightly bigger box for some leniency

        BoundingBox checkBounds = new BoundingBox(
                boatBounds.getMinX() - correction,
                boatBounds.getMinY() - correction,
                boatBounds.getMinZ() - correction,
                boatBounds.getMaxX() + correction,
                boatBounds.getMinY(),
                boatBounds.getMaxZ() + correction);

        int minX = (int) Math.floor(checkBounds.getMinX());
        int maxX = (int) Math.floor(checkBounds.getMaxX());
        int minY = (int) Math.floor(checkBounds.getMinY());
        int maxY = (int) Math.floor(checkBounds.getMaxY());
        int minZ = (int) Math.floor(checkBounds.getMinZ());
        int maxZ = (int) Math.floor(checkBounds.getMaxZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = boatLoc.getWorld().getBlockAt(x, y, z);

                    if (block.getType() == Material.AIR) {
                        continue;
                    }

                    if (block.getType() == Material.WATER ||
                            block.getType().isSolid() ||
                            waterLogBypass(block.getType())) {
                        // passing over water logged items broke the boat so I added this simple check to prevent that.
                        BoundingBox blockBounds = block.getBoundingBox();
                        if (blockBounds != null && blockBounds.overlaps(checkBounds)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean waterLogBypass(Material material) {
        return material == Material.KELP_PLANT ||
                material == Material.KELP ||
                material == Material.SEAGRASS ||
                material == Material.TALL_SEAGRASS ||
                material == Material.SEA_PICKLE ||
                material == Material.BUBBLE_COLUMN;
    }

    private enum terrain {
        water,
        ice,
        land
    }
}