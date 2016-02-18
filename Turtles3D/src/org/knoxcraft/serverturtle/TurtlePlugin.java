package org.knoxcraft.serverturtle;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Stack;
import java.util.concurrent.LinkedBlockingQueue;

import org.knoxcraft.database.KCTScriptAccess;
import org.knoxcraft.hooks.KCTUploadHook;
import org.knoxcraft.jetty.server.JettyServer;
import org.knoxcraft.netty.server.HttpUploadServer;
import org.knoxcraft.turtle3d.KCTScript;
import org.knoxcraft.turtle3d.TurtleCompiler;
import org.knoxcraft.turtle3d.TurtleException;

import net.canarymod.Canary;
import net.canarymod.api.ai.AIBase;
import net.canarymod.api.ai.AIManager;
import net.canarymod.api.entity.EntityType;
import net.canarymod.api.entity.living.animal.CanaryRabbit;
import net.canarymod.api.entity.living.animal.CanaryWolf;
import net.canarymod.api.entity.living.animal.EntityAnimal;
import net.canarymod.api.entity.living.humanoid.Player;
import net.canarymod.api.world.World;
import net.canarymod.api.world.position.Location;
import net.canarymod.chat.MessageReceiver;
import net.canarymod.commandsys.Command;
import net.canarymod.commandsys.CommandListener;
import net.canarymod.database.DataAccess;
import net.canarymod.database.Database;
import net.canarymod.database.exceptions.DatabaseReadException;
import net.canarymod.database.exceptions.DatabaseWriteException;
import net.canarymod.hook.HookHandler;
import net.canarymod.hook.player.ConnectionHook;
import net.canarymod.hook.player.DisconnectionHook;
import net.canarymod.hook.world.WeatherChangeHook;
import net.canarymod.logger.Logman;
import net.canarymod.plugin.Plugin;
import net.canarymod.plugin.PluginListener;
import net.minecraft.entity.passive.EntityWolf;

public class TurtlePlugin extends Plugin implements CommandListener, PluginListener {

    private HttpUploadServer httpServer;
    private JettyServer jettyServer;
    public static Logman logger;
    private ScriptManager scripts;
    private HashMap<String, Stack<Stack<BlockRecord>>> undoBuffers;  //PlayerName->buffer
    
    private Map<String, WorkerThread> workerMap=new HashMap<String,WorkerThread>();

    //////////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     */
    public TurtlePlugin() {
        TurtlePlugin.logger = getLogman();
        scripts = new ScriptManager();
        undoBuffers = new HashMap<String, Stack<Stack<BlockRecord>>>();
        
    }

    /**
     * Called when plugin is disabled.
     */
    @Override
    public void disable() {
        if (httpServer!=null) {
            httpServer.disable();
        }
        if (jettyServer!=null) {
            jettyServer.disable();
        }
    }

    /**
     * Called when plugin is enabled. 
     * @return
     */
    @Override
    public boolean enable() {
        try {
            getLogman().info("Registering Knoxcraft Turtles plugin");
            Canary.hooks().registerListener(this, this);
            
            // TODO: these seem to have no effect; figure out why!
            boolean b1=Canary.getServer().consoleCommand("weather clear 1000000");
            boolean b2=Canary.getServer().consoleCommand("gamerule doDaylightCycle false");
            logger.trace(String.format("Did weather work? %s did daylight work? %s", b1, b2));
            
            jettyServer=new JettyServer();
            jettyServer.enable(logger);
            
            //httpServer=new HttpUploadServer();
            //httpServer.enable(getLogman());
            
            getLogman().info("Enabling "+getName() + " Version " + getVersion()); 
            getLogman().info("Authored by "+getAuthor());
            Canary.commands().registerCommands(this, this, false);

            lookupFromDB();

            return true;
        } catch (Exception e){
            if (jettyServer!=null) {
                jettyServer.disable();
            }
            if (httpServer!=null) {
                httpServer.disable();
            }
            logger.error("Cannot initialize TurtlePlugin", e);
            return false;
        }
    }

    /**
     * Load the latest version of each script from the DB for each player on this world
     * TODO Check how Canary handles worlds; do we have only one XML file of scripts
     * for worlds and should we include the world name or world ID with the script?
     */
    private void lookupFromDB() {
        KCTScriptAccess data=new KCTScriptAccess();
        List<DataAccess> results=new LinkedList<DataAccess>();
        Map<String,KCTScriptAccess> mostRecentScripts=new HashMap<String,KCTScriptAccess>();

        try {
            Map<String,Object> filters=new HashMap<String,Object>();
            Database.get().loadAll(data, results, filters);
            for (DataAccess d : results) {
                KCTScriptAccess scriptAccess=(KCTScriptAccess)d;
                // Figure out the most recent script for each player-scriptname combo
                String key=scriptAccess.playerName+"-"+scriptAccess.scriptName;
                if (!mostRecentScripts.containsKey(key)) {
                    mostRecentScripts.put(key, scriptAccess);
                } else {
                    if (scriptAccess.timestamp > mostRecentScripts.get(key).timestamp) {
                        mostRecentScripts.put(key,scriptAccess);
                    }
                }
                logger.trace(String.format("from DB: player %s has script %s at time %d%n", 
                        scriptAccess.playerName, scriptAccess.scriptName, scriptAccess.timestamp));
            }
            TurtleCompiler turtleCompiler=new TurtleCompiler(logger);
            for (KCTScriptAccess scriptAccess : mostRecentScripts.values()) {
                try {
                    KCTScript script=turtleCompiler.parseFromJson(scriptAccess.json);
                    script.setLanguage(scriptAccess.language);
                    script.setScriptName(scriptAccess.scriptName);
                    script.setSourceCode(scriptAccess.source);
                    script.setPlayerName(scriptAccess.playerName);

                    scripts.putScript(scriptAccess.playerName, script);
                    logger.info(String.format("Loaded script %s for player %s", 
                            scriptAccess.scriptName, scriptAccess.playerName));
                } catch (TurtleException e){
                    logger.error("Internal Server error", e);
                }
            }
        } catch (DatabaseReadException e) {
            logger.error("cannot read DB", e);
        }
    }
    
    class WorkerThread extends Thread {
        private Queue<KCTScript> workQueue=new LinkedBlockingQueue<KCTScript>();
        private Player player;
        
        WorkerThread(Player player) {
            this.player=player;
        }
        
        void addWorkToQueue(KCTScript script) {
            getLogman().info(String.format("Putting script named %s into queue for %s", script.getScriptName(), script.getPlayerName()));
            workQueue.offer(script);
        }
        private boolean done=false;
        
        public void run() {
            getLogman().debug(String.format("worker thread for %s starting!", player.getName()));
            while (!done) {
                KCTScript script=workQueue.poll();
                if (script==null) {
                    continue;
                }
                getLogman().trace(String.format("worker thread for %s just got script named %s", player.getName(), script.getScriptName()));
                Turtle t=new Turtle(getLogman());
                t.turtleInit(player);
                //TODO: make this a loop through KCTCommands
                t.executeScript(script);
            }
            getLogman().debug("%s worker thread exiting", player.getName());
        }

        public void setDone() {
            done=true;
        }
    }

    //HOOK HANDLERS

    /**
     * Login hook that disallows breaking or placing blocks, 
     * except by using code that students have uploaded.
     * 
     * This turns out to be easier than enabling flight in adventure mode.
     */
    @HookHandler
    public void onLogin(ConnectionHook hook) {
        hook.getPlayer().setCanBuild(false);
        logger.debug(String.format("player %s can build? %s", hook.getPlayer().getName(), hook.getPlayer().canBuild()));
        String playerName=hook.getPlayer().getName().toLowerCase();
        
        if (!workerMap.containsKey(playerName) || !workerMap.get(playerName).isAlive()) {
            getLogman().info(String.format("%s creating a worker thread and starting it", playerName));
            WorkerThread t=new WorkerThread(hook.getPlayer());
            workerMap.put(playerName, t);
            t.start();
        }
    }
    
    @HookHandler
    public void onLogout(DisconnectionHook hook) {
        if (workerMap.containsKey(hook.getPlayer().getName())) {
            WorkerThread t=workerMap.get(hook.getPlayer().getName());
            t.setDone();
        }
    }
    
    /**
     * TODO: Fix this hook. This doesn't seem to get called. I would like to shut rain off every time it starts raining.
     * 
     * @param hook
     */
    @HookHandler
    public void onWeatherChange(WeatherChangeHook hook) {
        World w=hook.getWorld();
        logger.info(String.format("Weather change hook called, is raining? %s", w.isRaining()));
        if (w.isRaining()) {
            logger.info(String.format("Set raining to false"));
            w.setRaining(false);
            //w.setRainTime(0);
        }
    }
    
    /**
     * Hook called when scripts are uploaded to the server
     * 
     * @param hook
     */
    @HookHandler
    public void uploadJSON(KCTUploadHook hook) {
        logger.trace("Hook called!");
        //add scripts to manager and db
        Collection<KCTScript> list = hook.getScripts();
        for (KCTScript script : list)  {
            scripts.putScript(hook.getPlayerName().toLowerCase(), script);

            // This will create the table if it doesn't exist
            // and then insert data for the script into a new row
            KCTScriptAccess data=new KCTScriptAccess();
            data.json=script.toJSONString();
            data.source=script.getSourceCode();
            data.playerName=hook.getPlayerName();
            data.scriptName=script.getScriptName();
            data.language=script.getLanguage();
            try {
                Database.get().insert(data);
            } catch (DatabaseWriteException e) {
                logger.error(e);
            }
        }
    }  

    //COMMANDS

    /**
     * List all the scripts.
     * 
     * @param sender
     * @param args
     */
    @Command(aliases = { "scripts", "sc", "ls" },
            description = "List KCTScripts",
            permissions = { "" },
            toolTip = "/ls")
    public void listScripts(MessageReceiver sender, String[] args) {
        logger.debug(String.format("name of sender is: %s", sender.getName().toLowerCase()));
        sender.message(String.format("%s is listing turtle scripts (programs)", sender.getName().toLowerCase()));
        if (args.length>1) {
            String playerName=args[1].toLowerCase();
            if (playerName.equalsIgnoreCase("all")) {
                // List all scripts for all players
                Map<String,Map<String,KCTScript>> allScriptMap=scripts.getAllScripts();
                for (Entry<String,Map<String,KCTScript>> entry : allScriptMap.entrySet()) {
                    playerName=entry.getKey();
                    for (Entry<String,KCTScript> entry2 : entry.getValue().entrySet()) {
                        sender.message(String.format("%s has the script %s", playerName, entry2.getKey()));
                    }
                }
            } else {
                // List only scripts for the given player
                Map<String,KCTScript> map=scripts.getAllScriptsForPlayer(playerName);
                for (Entry<String,KCTScript> entry : map.entrySet()) {
                    sender.message(String.format("%s has script %s"), playerName, entry.getKey());
                }
            }
        } else {
            Map<String,KCTScript> map=scripts.getAllScriptsForPlayer(sender.getName().toLowerCase());
            if (map==null) {
                // hacky way to use a case-insensitive key in a map
                // in the future, be sure to make all keys lower-case
                map=scripts.getAllScriptsForPlayer(sender.getName());
            }
            if (map==null) {
                sender.message(String.format("We cannot find any scripts for %s", sender.getName()));
            }
            for (Entry<String,KCTScript> entry : map.entrySet()) {
                logger.debug(String.format("%s => %s", entry.getKey(), entry.getValue().getLanguage()));
                sender.message(String.format("%s => %s", entry.getKey(), entry.getValue().getLanguage()));
            }
        }
    }
    
    static void removeAITasks(EntityAnimal e) {
        AIManager aiman=e.getAITaskManager();
        boolean done=false;
        while (!done) {
            AIBase task=aiman.getTask(AIBase.class);
            if (task==null) {
                break;
            }
            logger.trace("removing task with class %s\n", task.getClass());
            //done=!aiman.removeTask(AIBase.class);
            //System.out.println("removing task!");
        }
    }
    
    
    
    
    
    /**
     * Run a script using a Sprite.
     * 
     * @param sender
     * @param args
     */
    @Command(
            aliases = { "q" },
            description = "Enqueue work",
            permissions = { "" },
            toolTip = "/q <scriptName> [playerName]")
    public void enqueue(MessageReceiver sender, String[] args) {
        logger.info("q invoked");
        if (args.length<2)  {  //not enough arguments
            sender.message("Not enough arguments.");
            return;
        }

        String scriptName = args[1]; //get desired script name
        String senderName = sender.getName().toLowerCase();

        String playerName = senderName; //executing own script (default)        
        if (args.length==3)  {  //executing someone else's script
            playerName = args[2];
        }
        
        //Get script from map
        logger.trace(String.format("%s is looking for %s", playerName, scriptName));
        KCTScript script = lookupScript(sender, scriptName, playerName);
        
        for (Entry<String,WorkerThread> entry : workerMap.entrySet()) {
            getLogman().info(String.format("%s => a thread", entry.getKey()));
        }
        
        workerMap.get(playerName).addWorkToQueue(script);
    }
    
    /**
     * Run a script using a Sprite.
     * 
     * @param sender
     * @param args
     */
    @Command(
            aliases = { "go" },
            description = "Invoke a script using a sprite",
            permissions = { "" },
            toolTip = "/go <scriptName> [playerName]")
    public void go(MessageReceiver sender, String[] args) {
        logger.info("go invoked");
        if (args.length<2)  {  //not enough arguments
            sender.message("Not enough arguments.");
            return;
        }

        String scriptName = args[1]; //get desired script name
        String senderName = sender.getName().toLowerCase();

        String playerName = senderName; //executing own script (default)        
        if (args.length==3)  {  //executing someone else's script
            playerName = args[2];
        }
        
        //Get script from map
        logger.trace(String.format("%s is looking for %s", playerName, scriptName));
        KCTScript script = lookupScript(sender, scriptName, playerName);
        
        Player player=sender.asPlayer();
        //Vector3D dir=player.getForwardVector();
        World world=player.getWorld();
        
        Location location=new Location(world, player.getPosition());
        CanaryRabbit canaryRabbit=(CanaryRabbit)Canary.factory().getEntityFactory().newEntityLiving(EntityType.RABBIT, location);

        logger.trace("can it spawn? "+canaryRabbit.canSpawn());
        MagicBunny bunny=new MagicBunny(canaryRabbit, logger);
        TheoreticalTurtle turtle=new TheoreticalTurtle(sender, bunny, script, logger);
        bunny.getAITaskManager().addTask(Integer.MAX_VALUE, new KCTAITask(turtle, undoBuffers));
        
        logger.trace("bunny can spawn? "+bunny.canSpawn());
        boolean bunnySpawn=bunny.spawn();
        logger.trace("bunny spawn "+bunnySpawn);
        
        
    }
    
    /**
     * Invoke a script.
     * 
     * @param sender
     * @param args
     */
    @Command(
            aliases = { "invoke", "in" },
            description = "Invoke a script.",
            permissions = { "" },
            toolTip = "/in <scriptName> [playerName]")
    public void invoke(MessageReceiver sender, String[] args)  {
    	
        if (args.length<2)  {  //not enough arguments
            sender.message("Not enough arguments.");
            return;
        }

        String scriptName = args[1]; //get desired script name
        String senderName = sender.getName().toLowerCase();

        String playerName = senderName; //executing own script (default)        
        if (args.length==3)  {  //executing someone else's script
            playerName = args[2];
        }

        //Create turtle
//        Turtle turtle = new Turtle(logger);
//        turtle.turtleInit(sender);
        //Sprite sprite = new Sprite(new EntityWolf((net.minecraft.world.World) sender.asPlayer().getWorld()), logger); //??? Is this good? #TODO
        // TODO: use a CanaryWolf instead of an EntityWolf
        
        CanaryWolf canaryWolf=(CanaryWolf)Canary.factory().getEntityFactory().newEntity(EntityType.WOLF, sender.asPlayer().getWorld());
        EntityWolf entityWolf=canaryWolf.getHandle();
        
        Sprite sprite = new Sprite(entityWolf, logger,sender);
        //sprite.sInit(sender);
        //Get script from map
        logger.trace(String.format("%s is looking for %s", playerName, scriptName));
        KCTScript script = lookupScript(sender, scriptName, playerName);
        
        
        //KCTScript script = null;
        //try  {     
//            for (String p : scripts.getAllScripts().keySet()) {
//                logger.trace("Player name: "+p);
//                for (String s : scripts.getAllScriptsForPlayer(p).keySet()) {
//                    logger.trace(String.format("Player name %s has script named %s", p, s));
//                }
//            }
//            KCTScript script = scripts.getScript(playerName, scriptName);
//            if (script==null) {
//                logger.warn(String.format("player %s cannot find script %s", playerName, scriptName));
//                sender.asPlayer().message(String.format("%s, you have no script named %s", playerName, scriptName));
//                return;
//            }
//        }  catch (Exception e)  {
//            //turtle.turtleConsole("Script failed to load!");
//        	//sprite.sConsole("Script failed to load!");
//            sender.message("Script failed to load!");
//            logger.error("Script failed to load", e);
//        }

        //Execute script    
        try  {
            sprite.execute(script);
        }  catch (Exception e)  {
            sprite.sConsole("Script failed to execute!");
            logger.error("Script failed to execute", e);
        }

        //add script's blocks to undo buffer
        try  {            
            //create buffer if doesn't exist
            if (!undoBuffers.containsKey(senderName)) {  
                undoBuffers.put(senderName, new Stack<Stack<BlockRecord>>());
            }    
            //add to buffer
            undoBuffers.get(senderName).push(sprite.getOldBlocks());            
        }  catch (Exception e)  {
            sprite.sConsole("Failed to add to undo buffer!");
            logger.error("Faile to add to undo buffer", e);
        }
    }

    /**
     * @param sender
     * @param scriptName
     * @param playerName
     * @return
     */
    private KCTScript lookupScript(MessageReceiver sender, String scriptName,String playerName)
    {
        KCTScript script = scripts.getScript(playerName, scriptName);
        if (script==null) {
            logger.warn(String.format("player %s cannot find script %s", playerName, scriptName));
            sender.asPlayer().message(String.format("%s, you have no script named %s", playerName, scriptName));
            return null;
        }
        return script;
    }

    /**
     * Undo the previous script.
     * 
     * @param sender
     * @param args
     */
    @Command(
            aliases = { "undo"},
            description = "Undo the previous script.",
            permissions = { "" },
            toolTip = "/undo")
    public void undo(MessageReceiver sender, String[] args)  {

        String senderName = sender.getName().toLowerCase();

        //sender has not executed any scripts
        if (!undoBuffers.containsKey(senderName))  {  
            sender.message("You have not executed any scripts to undo!");

        }  else {  //buffer exists

            //get buffer
            Stack<Stack<BlockRecord>> buffer = undoBuffers.get(senderName);

            if (buffer.empty()){  //buffer empty
                sender.message("There are no more scripts to undo!");

            }  else  {  //okay to undo last script executed

                //get buffer
                Stack<BlockRecord> blocks = buffer.pop();

                //replace original blocks
                while(!blocks.empty())  {
                    
                    BlockRecord b=blocks.pop();
                    World world = sender.asPlayer().getWorld();
                    world.setBlockAt(b.getBlock().getPosition(), b.getBlock()); //Whats in buffer
                    //blocks.pop().revert();
                }
            }
        }
    }
}