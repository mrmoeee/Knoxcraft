package org.knoxcraft.turtle3d;

import static org.knoxcraft.turtle3d.KCTCommand.BACKWARD;
import static org.knoxcraft.turtle3d.KCTCommand.BLOCKPLACEMODE;
import static org.knoxcraft.turtle3d.KCTCommand.BLOCKTYPE;
import static org.knoxcraft.turtle3d.KCTCommand.DEGREES;
import static org.knoxcraft.turtle3d.KCTCommand.DIR;
import static org.knoxcraft.turtle3d.KCTCommand.DIST;
import static org.knoxcraft.turtle3d.KCTCommand.DOWN;
import static org.knoxcraft.turtle3d.KCTCommand.FORWARD;
import static org.knoxcraft.turtle3d.KCTCommand.LEFT;
import static org.knoxcraft.turtle3d.KCTCommand.PLACEBLOCKS;
import static org.knoxcraft.turtle3d.KCTCommand.RIGHT;
import static org.knoxcraft.turtle3d.KCTCommand.SETBLOCK;
import static org.knoxcraft.turtle3d.KCTCommand.SETDIRECTION;
import static org.knoxcraft.turtle3d.KCTCommand.SETPOSITION;
import static org.knoxcraft.turtle3d.KCTCommand.TURNLEFT;
import static org.knoxcraft.turtle3d.KCTCommand.TURNRIGHT;
import static org.knoxcraft.turtle3d.KCTCommand.UP;
import static org.knoxcraft.turtle3d.KCTCommand.X;
import static org.knoxcraft.turtle3d.KCTCommand.Y;
import static org.knoxcraft.turtle3d.KCTCommand.Z;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Class for procedural-style programming. Students must create instances using the
 * createTurtle() factory method rather than with a constructor. This is for technical
 * reasons having to do with how we handle the code server-side. For those interested
 * in the gory details: Basically, we need to create instances, then get the instances
 * after the program is finished executing. Since the Minecraft server may be running
 * in a multiuser environment, this means that we need to use the executing thread
 * to find the Turtles created by that thread.
 * 
 * From the student perspective, use the createTurtle() method to create a Turtle, and then
 * use the turtle to lay out blocks.
 * 
 * 
 * @author ppypp emhastings hahaha
 *
 */
public class Turtle3D
{
    
    /* Sample JSON commands:
    {
   "scriptname" : "script-test",
   "commands" : [
       {"cmd" : "forward",
           "args" : {"dist" : 10}},
       {"cmd" : "turnRight",
           "args" : {"degrees" : 90}}
   ]
   }
   */
    
    private String scriptName;
    private KCTScript script;
    
    // Map for the static factory pattern.
    // TODO: How to handle race conditions for the turtleMap in multithreading?
    public static Map<Thread,Map<String,Turtle3D>> turtleMap=new LinkedHashMap<Thread,Map<String,Turtle3D>>();
   
    /**
     * Static factory instead of constructor
     * This lets us get the Turtle instances after running main, and then get their KCTScripts and generate 
     * the JSON code.
     * 
     * @param name The name of the turtle
     * @return The turtle with the given name. If you try to create multiple turtles with the same name
     * in the same main method, you will actually keep getting back the same turtle!
     */
    public static Turtle3D createTurtle(String name) {
        Thread currentThread=Thread.currentThread();
        if (!turtleMap.containsKey(currentThread)) {
            turtleMap.put(currentThread, new HashMap<String,Turtle3D>());
        }
        Map<String,Turtle3D> map=turtleMap.get(currentThread);
        Turtle3D turtle=new Turtle3D(name);
        map.put(name, turtle);
        return turtle;
    }
    
    /**
     * Private constructor to enforce static factory pattern
     * 
     * @param scriptName The name of the script
     */
    private Turtle3D(String scriptName) {
        script=new KCTScript(scriptName);
    }
    
    /**
     * No need for students to call this method.
     * 
     * @return The name of the script
     */
    public String getScriptName() {
        return this.scriptName;
    }
    
    /**
     * No need for students to call this method.
     * 
     * @return The script
     */
    public KCTScript getScript() {
        return this.script;
    }
    
    /**
     * Move the turtle forward the given distance.
     * 
     * @param distance The distance to move
     */
    public void forward(int distance) {
        KCTCommand cmd=new KCTCommand(FORWARD, JSONUtil.makeArgMap(DIST, distance));
        script.addCommand(cmd);
    }
    
    /**
     * Move the turtle to the right the given distance.
     * This would be called "strafe right" in most FPS games; in other words, 
     * this will not cause the turtle to turn to the right.
     * 
     * @param distance
     */
    public void right(int distance) {
        KCTCommand cmd=new KCTCommand(RIGHT, JSONUtil.makeArgMap(DIST, distance));
        script.addCommand(cmd);
    }
    
    /**
     * Move the turtle to the left the given distance.
     * This would be called "strafe left" in most FPS games; in other words, 
     * this will not cause the turtle to turn to the left.
     * 
     * @param distance
     */
    public void left(int distance) {
        KCTCommand cmd=new KCTCommand(LEFT, JSONUtil.makeArgMap(DIST, distance));
        script.addCommand(cmd);
    }
    
    /**
     * Move the turtle backward the given distance.
     * 
     * @param distance The distance to move
     */
    public void backward(int distance) {
        KCTCommand cmd=new KCTCommand(BACKWARD, JSONUtil.makeArgMap(DIST, distance));
        script.addCommand(cmd);
    }
    
    /**
     * Turn the turtle to the right the given number of degrees.
     * (Rounds to multiples of 45 deg)
     * 
     * @param degrees The degrees to turn
     */
    public void turnRight(int degrees){
        KCTCommand cmd=new KCTCommand(TURNRIGHT, JSONUtil.makeArgMap(DEGREES, degrees));
        script.addCommand(cmd);
    }
    
    /**
     * Turn the turtle to the left the given number of degrees.
     * (Rounds to multiples of 45 deg)
     * 
     * @param degrees The degrees to turn
     */public void turnLeft(int degrees){
        KCTCommand cmd=new KCTCommand(TURNLEFT, JSONUtil.makeArgMap(DEGREES, degrees));
        script.addCommand(cmd);
    }
    
     /**
      * Move the turtle up the given distance.
      * 
      * @param distance The distance to move
      */
     public void up(int distance){
         KCTCommand cmd=new KCTCommand(UP, JSONUtil.makeArgMap(DIST, distance));
         script.addCommand(cmd);
     }
     
     /**
      * Move the turtle down the given distance.
      * 
      * @param distance The distance to move
      */
     public void down(int distance){
         KCTCommand cmd=new KCTCommand(DOWN, JSONUtil.makeArgMap(DIST, distance));
         script.addCommand(cmd);
     }
     
     /**
      * Set turtle block placement mode
      * 
      * @param mode The mode
      */
     public void setBlockPlace(boolean mode){
         KCTCommand cmd=new KCTCommand(PLACEBLOCKS, JSONUtil.makeArgMap(BLOCKPLACEMODE, mode));
         script.addCommand(cmd);
     }
     
     /**
      * Set turtle block type (int-based)
      * 
      * @param type The int representing the block type
      */
     public void setBlock(int type){
         KCTCommand cmd=new KCTCommand(SETBLOCK, JSONUtil.makeArgMap(BLOCKTYPE, type));
         script.addCommand(cmd);
     }
     
     /**
      * Set block type-- using constants from CanaryMod
      * 
      * @param type The block type
      */
    public void setBlock(KCTBlockTypes type) {
        String id= type.name();
        //we will be filling this with our own code eventually with soemthing like Minecraft:WOOL:light_blue
//        if (type.getData()!=0) {
//            id+=":"+type.getData();
//        }
        KCTCommand cmd=new KCTCommand(SETBLOCK, JSONUtil.makeArgMap(BLOCKTYPE, id));         script.addCommand(cmd);
     }
     
     /**
      * Set turtle block type (string-based).
      * Not recommended for student use.
      * 
      * @param type The String name of the block type
      */
     protected void setBlock(String type){
         KCTCommand cmd=new KCTCommand(SETBLOCK, JSONUtil.makeArgMap(BLOCKTYPE, type));
         script.addCommand(cmd);
     }
     
     /**
      * Set the turtle's relative position
      * 
      * @param position [x, y, z] The position as an array of ints
      */
     public void setPosition(int[] position){
         int x = position[0];
         int y = position[1];
         int z = position[2];
         KCTCommand cmd=new KCTCommand(SETPOSITION, JSONUtil.makeArgMap(X, x, Y, y, Z, z));
         script.addCommand(cmd);
     }
     
     /**
      * Set the turtle's relative position
      * 
      * @param x The x coord
      * @param y The y coord
      * @param z The z coord
      */
     public void setPosition(int x, int y, int z){
         KCTCommand cmd=new KCTCommand(SETPOSITION, JSONUtil.makeArgMap(X, x, Y, y, Z, z));
         script.addCommand(cmd);
     }
     
     /**
      * Set the turtle's direction using direction constants (i.e. Direction.EAST, Direction.NORTHEAST, etc) 
      * 
      * @param direction The direction
      */
    public void setDirection(TurtleDirection direction) {
        // FIXME: need methods to encode/decode direction to an int
        // FIXME: translate to sponge
    	
    	//http://docs.visualillusionsent.net/CanaryLib/1.0-RC-3/src-html/net/canarymod/api/world/position/Direction.html#line.19
    	//original canarymod source to reference for directions we are using.
    	//We may have to make our own class for Direction as well as the BlockTypes class. 
    	//For direction there are extra compass directions which are not useful to us unless we want to use them in the turtle class. 
    	//Otherwise, we should just make a class that does not have those extra directions in it.
    	
        KCTCommand cmd=new KCTCommand(SETDIRECTION, JSONUtil.makeArgMap(DIR, direction.getIntValue()));
        script.addCommand(cmd);
     }
}