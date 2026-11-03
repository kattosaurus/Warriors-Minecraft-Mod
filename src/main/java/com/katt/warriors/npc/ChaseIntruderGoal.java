package com.katt.warriors.npc;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Random;

public class ChaseIntruderGoal extends Goal {

    private final ClanCatNpc npc;
    private final float chanceToActivate;
    private final double detectRadius;
    private final double giveUpRadius;
    private final Random rng = new Random();

    private Player target;
    private int cooldown = 0;

    public ChaseIntruderGoal(ClanCatNpc npc, float chanceToActivate,
                             double detectRadius, double giveUpRadius) {
        this.npc = npc;
        this.chanceToActivate = chanceToActivate;
        this.detectRadius = detectRadius;
        this.giveUpRadius = giveUpRadius;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        Player nearest = npc.level().getNearestPlayer(npc, detectRadius);
        if (nearest == null) return false;
        if (!npc.isIntruder(nearest)) return false;
        if (rng.nextFloat() >= chanceToActivate) {
            cooldown = 100;
            return false;
        }
        target = nearest;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (target == null || !target.isAlive()) return false;
        if (!npc.isIntruder(target)) {
            return false;
        }
        double dist = npc.distanceTo(target);
        return dist <= giveUpRadius;
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        npc.setSpeed(0.0f);
        target = null;
        cooldown = 200;
    }

    @Override
    public void tick() {
        if (target == null) return;
        npc.getLookControl().setLookAt(target, 30.0f, 30.0f);
        npc.getNavigation().moveTo(target, 1.4);

        if (npc.distanceTo(target) < 2.0) {
            npc.doHurtTarget(target);
        }
    }
}
