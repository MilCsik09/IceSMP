package hu.taliann.icesmp.prologue;

import java.nio.file.Files;
import java.nio.file.Path;

/** Pure math plus source-contract regressions for the Season 0 authority and recovery flow. */
public final class PrologueRegressionSuite {
    private PrologueRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        progressionCeilingAndCatchUp();
        participantScalingIsBounded();
        pauseClockStopsTimeout();
        cleanupAndPauseContracts();
        victoryRaceContracts();
        transactionAndAuthorityContracts();
        worldGateAndRehearsalContracts();
        liveOpsControlContracts();
        foliaPackageContracts();
        System.out.println("Prologue regression suite passed.");
    }

    private static void progressionCeilingAndCatchUp() {
        final int capXp=PrologueProgression.experienceAtLevelStart(25,100,20);
        check(capXp>0,"level-25 threshold must be positive");
        check(PrologueProgression.clampExperienceToLevelCap(capXp+1_000_000,25,100,20)==capXp,
                "Season 0 XP can bank above the level-25 boundary");
        check(PrologueProgression.clampExperienceToLevelCap(capXp-10,25,100,20)==capXp-10,
                "valid XP below the cap is changed");
        check(PrologueProgression.applyMultiplier(100,1.75D)==175,"catch-up multiplier drifted");
        check(PrologueProgression.applyMultiplier(100,1.0D)==100,"disabled catch-up changes XP");
    }

    private static void participantScalingIsBounded() {
        check(PrologueScaling.effectivePlayers(0,5,45)==5,"scaling ignores minimum");
        check(PrologueScaling.effectivePlayers(99,5,45)==45,"scaling ignores maximum");
        int ten=PrologueScaling.mobCount(4,10,5,45,.4D,4,28);
        int fortyFive=PrologueScaling.mobCount(4,45,5,45,.4D,4,28);
        check(ten>=4&&fortyFive>=ten&&fortyFive<=28,"mob scaling is not bounded/monotonic");
        double boss=PrologueScaling.bossHealth(500D,45,5,45,.075D,4D);
        check(boss>=500D&&boss<=2_000D,"boss scaling exceeds configured bound");
    }

    private static void pauseClockStopsTimeout() {
        ProloguePauseClock clock=new ProloguePauseClock(900_000L,0L);
        check(clock.remainingMillis(480_000L)==420_000L,"running clock did not consume elapsed time");
        clock.pause(480_000L);
        check(clock.remainingMillis(1_680_000L)==420_000L,"pause time consumed encounter timeout");
        check(!clock.expired(9_000_000L),"paused encounter expired");
        clock.resume(1_680_000L);
        check(clock.remainingMillis(1_800_000L)==300_000L,"resume lost remaining timeout");
        check(clock.expired(2_100_000L),"resumed encounter did not expire after remaining budget");
    }

    private static void cleanupAndPauseContracts() throws Exception {
        String encounter=source("src/main/java/hu/taliann/icesmp/prologue/PrologueEncounterEngine.java");
        check(!encounter.contains("Bukkit.getEntity("),"Prologue cleanup uses global entity lookup");
        check(encounter.contains("TransientEntities.removeById(plugin,id)")
                        &&encounter.contains("liveMobs.clear()")&&encounter.contains("entityEncounters.clear()"),
                "encounter cleanup does not use/retire transient scheduler handles idempotently");
        check(encounter.contains("pauseActive()")&&encounter.contains("resumeActive()")
                        &&encounter.contains("m.setAI(!paused)")&&encounter.contains("m.setInvulnerable(paused)"),
                "active encounter entities do not follow pause state");
        check(encounter.contains("ProloguePauseClock")&&encounter.contains("runAtFixedRate")
                        &&!encounter.contains("timeoutTicks ="),"timeout is still an absolute one-shot task");
        check(encounter.contains("EventPriority.HIGHEST")&&encounter.contains("event.setCancelled(true)"),
                "paused Prologue combat is not blocked in both directions");
        check(encounter.contains("if(e.paused.get())")&&encounter.contains("runDelayed(plugin,owner"),
                "pending spawn work can continue through pause");

        String manager=source("src/main/java/hu/taliann/icesmp/prologue/PrologueManager.java");
        check(manager.contains("pause-started-at")&&manager.contains("pause-accumulated-millis")
                        &&manager.contains("finalePhaseAgeMillis"),"phase timing does not exclude pause/restart time");
        String run=source("src/main/java/hu/taliann/icesmp/prologue/PrologueFinaleRunState.java");
        check(run.contains("paused-encounter.remaining-millis")&&run.contains("recordPausedEncounter")
                        &&run.contains("remainingTimeoutFor"),"paused restart has no durable remaining-time receipt");
        String finale=source("src/main/java/hu/taliann/icesmp/prologue/PrologueFinaleManager.java");
        check(finale.contains("safety.pause(actor)")&&finale.contains("safety.resume(actor")
                        &&finale.contains("if(!rehearsal&&state.paused())return")
                        &&finale.contains("remainingTimeoutFor(state.finaleId()"),
                "orchestrator does not preserve real pause/restart semantics");
    }

    private static void victoryRaceContracts() throws Exception {
        String encounter=source("src/main/java/hu/taliann/icesmp/prologue/PrologueEncounterEngine.java");
        int callback=encounter.indexOf("e.completion.run()");
        int finish=encounter.indexOf("e.finished.compareAndSet(false,true)",callback);
        check(callback>=0&&finish>callback,"boss encounter becomes spawnable before completion latch callback");
        check(encounter.contains("completionStarted.compareAndSet(false,true)"),"duplicate boss completion callback is not fenced");

        String safety=source("src/main/java/hu/taliann/icesmp/prologue/PrologueFinaleSafety.java");
        int latch=safety.indexOf("victoryObserved.compareAndSet(false,true)");
        int async=safety.indexOf("getAsyncScheduler().runNow",latch);
        int pending=safety.indexOf("markBossVictoryPending",async);
        int durable=safety.indexOf("recordBossVictory",pending);
        check(latch>=0&&async>latch&&pending>async&&durable>pending,
                "victory latch must fence spawn before off-region durable pending/victory writes");
        check(safety.contains("blocksBossSpawn()")&&safety.contains("markBossVictoryPersistenceFailure")
                        &&safety.contains("retryVictory"),"victory latency/failure is not fail-closed and recoverable");

        String manager=source("src/main/java/hu/taliann/icesmp/prologue/PrologueManager.java");
        check(manager.contains("boss-victory-pending")&&manager.contains("requireFinale(expected)")
                        &&manager.contains("recordBossVictory(UUID expected")
                        &&manager.contains("markBossVictoryPersistenceFailure"),
                "durable victory receipt is not finaleId-bound/idempotent");
        String finale=source("src/main/java/hu/taliann/icesmp/prologue/PrologueFinaleManager.java");
        check(finale.contains("safety.blocksBossSpawn()")&&finale.contains("safety.observeVictory"),
                "BOSS_FIGHT can spawn during victory persistence latency");
    }

    private static void transactionAndAuthorityContracts() throws Exception {
        String manager=source("src/main/java/hu/taliann/icesmp/prologue/PrologueManager.java");
        check(manager.contains("YamlStore.registerCriticalWrite(file)")&&manager.contains("restore(before)"),
                "Prologue world state is not atomic critical persistence");
        check(manager.contains("if(!gateUnlocked||!rewardsCommitted||!seasonOneStarted)"),
                "COMPLETED can commit before gate/reward/Season1 invariants");

        String settlement=source("src/main/java/hu/taliann/icesmp/prologue/PrologueFinaleSettlement.java");
        int gate=settlement.indexOf("unlockGateAfterVictory");
        int plan=settlement.indexOf("markRewardPlanCreated");
        int reward=settlement.indexOf("markRewardsCommitted");
        int chronicle=settlement.indexOf("publishExtraordinaryOnce");
        int monument=settlement.indexOf("recordPrologueOnce");
        int season=settlement.indexOf("prepareSeasonOne");
        int complete=settlement.indexOf("state.complete");
        check(gate>=0&&plan>gate&&reward>plan&&chronicle>reward&&monument>chronicle&&season>monument&&complete>season,
                "finale irreversible transaction chain is out of order");

        String rewards=source("src/main/java/hu/taliann/icesmp/prologue/PrologueRewardService.java");
        check(rewards.contains("repository().cached(playerId).isEmpty()")&&rewards.contains("grantEligibleWhenProfileReady")
                        &&rewards.contains("prologue.finaleParticipants().contains(playerId)"),
                "offline eligible participant has no replay-safe Profile v2 delivery");
        check(!rewards.contains("PersistentDataContainer")&&!rewards.contains("player.yml"),
                "prestige state bypasses PlayerProfile v2 authority");
        String transition=source("src/main/java/hu/taliann/icesmp/prologue/PrologueSeasonTransition.java");
        check(transition.contains("prologue-season-transition.yml")&&transition.contains("YamlStore.registerCriticalWrite(receiptFile)")
                        &&transition.contains("season-one-start")&&transition.contains("season.number\", 1"),
                "Season 1 fresh-start receipt is not crash-stable");
    }

    private static void worldGateAndRehearsalContracts() throws Exception {
        String portal=source("src/main/java/hu/taliann/icesmp/listeners/PortalGuardListener.java");
        check(portal.contains("CreateReason.FIRE")&&portal.contains("NETHER_PORTAL")
                        &&portal.contains("World.Environment.NETHER")&&portal.contains("PrologueContentPolicy.netherTraversalAvailable")
                        &&portal.contains("PrologueWorldAccess.within")&&portal.contains("Permissions.TERRITORY_BYPASS"),
                "single legitimate Nether gate authority is incomplete");
        check(portal.contains("END_PORTAL_FRAME")&&portal.contains("END_PORTAL"),"Prologue weakened End policy");
        String world=source("src/main/java/hu/taliann/icesmp/prologue/PrologueWorldAccess.java");
        check(world.contains("prologue-gate")&&world.contains("prologue-gathering")&&world.contains("prologue-breach")&&world.contains("prologue-boss"),
                "builder-defined Prologue anchors are incomplete");
        String finale=source("src/main/java/hu/taliann/icesmp/prologue/PrologueFinaleManager.java");
        check(finale.contains("rehearsal")&&finale.contains("settlement.visualAwakening()")
                        &&finale.contains("settlement.falseEnd(phaseAgeMillis())")&&finale.contains("settlement.gateAwakening()"),
                "rehearsal and production no longer share encounter/presentation routing");
        check(!between(finale,"if(rehearsal){","return;\n        }\n        settlement.falseEnd").contains("markRewardPlanCreated"),
                "rehearsal directly commits production reward state");
    }

    private static void foliaPackageContracts() throws Exception {
        try(var paths=Files.walk(Path.of("src/main/java/hu/taliann/icesmp/prologue"))){
            for(Path path:paths.filter(p->p.toString().endsWith(".java")).toList()){
                String value=Files.readString(path).replace("\r\n","\n");
                check(!value.contains("Bukkit.getEntity("),path+" uses global Bukkit entity lookup");
                check(!value.contains(".teleport("),path+" performs direct teleport mutation");
            }
        }
        String encounter=source("src/main/java/hu/taliann/icesmp/prologue/PrologueEncounterEngine.java");
        check(encounter.contains("getRegionScheduler().run")&&encounter.contains("getScheduler().runAtFixedRate")
                        &&encounter.contains("Bukkit.isOwnedByCurrentRegion(p)")
                        &&encounter.contains("p.getScheduler().run")&&encounter.contains("EventSpawnGuard.prepare"),
                "encounter world/entity/player mutations lost Folia scheduler ownership");
        String runtime=source("src/main/java/hu/taliann/icesmp/prologue/PrologueRuntime.java");
        check(runtime.contains("player.getScheduler().run")&&runtime.contains("getGlobalRegionScheduler().run"),
                "runtime player aggregation is not region scheduled");
    }

    private static void liveOpsControlContracts() throws Exception {
        String manager=source("src/main/java/hu/taliann/icesmp/prologue/PrologueManager.java");
        // A telepítés pillanatában induló óra volt az eredeti hiba: inert alapállapot nélkül a
        // timeline üres szerveren végigfuttatja az eszkalációt a nyitás előtt.
        check(manager.contains("\"world-events.prologue.initial-state\",\"DORMANT\""),
                "a Prologue alapállapota nem inert: a timeline a telepítéstől számolna");
        check(between(manager,"private static PrologueState parseState","private static PrologueStage parseStage")
                        .contains("return PrologueState.DORMANT"),
                "olvashatatlan initial-state nem inert állapotra esik");

        String timeline=source("src/main/java/hu/taliann/icesmp/prologue/PrologueTimelineController.java");
        check(timeline.contains("state!=PrologueState.UNSTABLE&&state!=PrologueState.BREACHING")
                        ||timeline.contains("state != PrologueState.UNSTABLE && state != PrologueState.BREACHING"),
                "a timeline DORMANT állapotban is léptetne");

        String arm=between(manager,"public boolean arm(","public void closeGate");
        check(arm.contains("state!=PrologueState.DORMANT")&&arm.contains("return false"),
                "az élesítés nem idempotens DORMANT-on kívül");
        check(arm.contains("stateChangedAt=stageChangedAt=finalePhaseChangedAt=now"),
                "az élesítés nem nullázza a stage-órát: a telepítés óta eltelt idő azonnal léptetne");

        String close=between(manager,"public void closeGate(","public void rewind(");
        check(close.contains("finaleVictory")&&close.contains("rewardsCommitted")
                        &&close.contains("seasonOneStarted")&&close.contains("PrologueState.COMPLETED"),
                "a gate close kiérdemelt győzelmet is visszavonhatna");

        String rewind=between(manager,"public void rewind(","private void requireFinale");
        for(String cleared:new String[]{"state=PrologueState.DORMANT","finaleId=null","participants.clear()",
                "bossDefeated=finaleVictory=bossVictoryPending=false",
                "gateUnlocked=rewardPlanCreated=rewardsCommitted=chronicleCommitted=monumentCommitted=false"}) {
            check(rewind.contains(cleared),"a visszatekerés nem törli: "+cleared);
        }

        String runtime=source("src/main/java/hu/taliann/icesmp/prologue/PrologueRuntime.java");
        String reset=between(runtime,"public void resetForTesting(","public void");
        int season=reset.indexOf("rollbackSeasonOne()");
        int wind=reset.indexOf("manager.rewind(");
        check(season>=0&&wind>season,
                "a szezon-visszaállításnak a Prologue-rewind ELŐTT kell futnia, különben az overlay "
                        +"Season 1 alatt kapcsolna vissza Season 0 tartalomkorlátra");
        check(reset.indexOf("forgetExtraordinary(\"prologue-gate-open\")")>=0
                        &&reset.indexOf("forgetPrologue(\"prologue-first-expedition\")")>=0,
                "a reset nem törli a krónika/emlékmű egyszeri kulcsait");
        String settlement=source("src/main/java/hu/taliann/icesmp/prologue/PrologueFinaleSettlement.java");
        check(settlement.contains("\"prologue-gate-open\"")&&settlement.contains("\"prologue-first-expedition\""),
                "a reset és a settlement kulcsai elcsúsztak");

        // Az advance a tartós checkpointon megy át, így győzelmet nem hamisíthat.
        String command=source("src/main/java/hu/taliann/icesmp/commands/PrologueCommand.java");
        String advance=between(command,"private void advance(","private static PrologueFinalePhase nextPhase");
        check(advance.contains("runtime.manager().checkpoint(")&&advance.contains("runtime.manager().setStage("),
                "az advance megkerüli a tartós checkpoint/stage utat");
        check(advance.contains("PrologueFinalePhase.COMPLETED")&&advance.contains("PrologueFinalePhase.ABORTED"),
                "az advance kézzel zárhatná le vagy szakíthatná meg a finálét");
        check(between(manager,"public void checkpoint(","public void recordParticipants")
                        .contains("phase.ordinal()<finalePhase.ordinal()"),
                "a checkpoint visszafelé is léptethető");
        check(command.contains("reset")&&command.contains("--force"),"a reset nincs force flag mögé zárva");
    }

    private static String between(String source,String start,String end){
        int from=source.indexOf(start);int to=from<0?-1:source.indexOf(end,from+start.length());
        return from>=0&&to>from?source.substring(from,to):"";
    }
    private static String source(String relative)throws Exception{return Files.readString(Path.of(relative)).replace("\r\n","\n");}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
