import http.client
import urllib
import time
import inspect
import os


VERSION='0.1'

'''
{"scriptname" : "phillipe", 
	"commands" : [
		{"cmd" : "forward", "args" : {"dist" : 10}}, 
		{"cmd" : "turnRight", "args" : {"degrees" : 90}}, 
		{"cmd" : "forward", "args" : {"dist" : 20}}
	]
}
'''

class Command:
	def __init__(self, cmd, args):
		self.command=cmd
		self.args=args
	def __str__(self):
		argList=[]
		for k,v in self.args.items():
			if type(v) == str:
				v='"%s"' % v
			elif type(v) == int:
				v='%d' % v
			elif v == True:
				v='true'
			elif v == False:
				v='false'

			argList.append('"%s" : %s' % (k, v))
		argStr=', '.join(argList)
		return '{"cmd" : "%s", "args" : {%s}}' % (self.command, argStr)

class Turtle:
	#TODO testing
	#TODO Add value support? (verify it atleast)
	def __init__(self, name):
		self.name=name
		self.commands=[]
	def forward(self, numBlocks):
		self.commands.append(Command('forward', {'dist' : numBlocks}))
	def backward(self, numBlocks):
		self.commands.append(Command('backward', {'dist' : numBlocks}))
	def right(self, numBlocks):
		self.commands.append(Command('right', {'dist' : numBlocks}))
	def left(self, numBlocks):
		self.commands.append(Command('left', {'dist' : numBlocks}))
	def up(self, numBlocks):
		self.commands.append(Command('up', {'dist' : numBlocks}))
	def down(self, numBlocks):
		self.commands.append(Command('down', {'dist' : numBlocks}))		
	def turnRight(self, numDegrees):
		self.commands.append(Command('turnRight', {'degrees' : numDegrees}))
	def turnLeft(self, numDegrees):
		self.commands.append(Command('turnLeft', {'degrees' : numDegrees}))
	def setPosition(self, x, y, z):
		self.commands.append(Command('setPosition', {'x' : x, 'y' : y, 'z' : z}))
	def setDirection(self, direction):
		self.commands.append(Command('setDirection', {'direction' : direction}))
	def blockPlace(self, place):
		self.commands.append(Command('placeBlocks', {'blockPlaceMode' : place}))
	def setBlock(self, blockType):
		self.commands.append(Command('setBlock', {'blockType' : blockType}))

	def toJson(self):
		# future work: use the actual Python json library instead of rewriting it...
		cmdList=[]
		for cmd in self.commands:
			cmdList.append(str(cmd))
		cmdStr=', '.join(cmdList) + "\n"
		return \
'''{"scriptname" : "%s",
	"commands" : [
	%s]
}''' % (self.name, cmdStr)

	

	def upload(self, url, minecraftName):
		conn = http.client.HTTPConnection(url)
		json = self.toJson()
		source = sourceCodeOfCaller()
		params = {'jsontext': json, 
			'sourcetext': source, 
			'language': 'python', 
			'playerName' : minecraftName, 
			'client' : 'pykc-%s' % VERSION}
		
		boundary='P8qZ0SA4n1v9T'+str(round(time.time() * 1000))
		# holy crap, building a multipart upload by hand is a huge pain in the arse
		# I could have saved a whole day if I had read the documentation more closely... Ugh.
		dataList=[]
		dataList.append('--'+boundary)
		for key, val in params.items():
			# Add boundary and header
			dataList.append('Content-Disposition: form-data; name="{0}"'.format(key))
			dataList.append('')
			dataList.append(val)
			dataList.append('--'+boundary)
		dataList[-1]+='--'
		dataList.append('')
		body = '\r\n'.join(dataList)
		#print(body)
		contentType = 'multipart/form-data; boundary={}'.format(boundary)
		headers = {"Content-Type" : contentType, "Accept" : "text/plain"}
		conn.request("POST", "/kctupload", body, headers)
		response = conn.getresponse()

		if response.status==200:
			print('Success!')
			print(response.status, response.reason)	
			print(response.read().decode("utf-8"))
		else:
			print('Failed to upload...')
			print(response.status, response.reason)	
			print(response.read().decode("utf-8"))

def sourceCodeOfCaller():
	firstframe=inspect.getouterframes(inspect.currentframe())[-1][1]
	dir=os.path.dirname(os.path.abspath(firstframe))
	try:
		with open('%s/%s' % (dir, firstframe), 'r') as content_file:
			return content_file.read()
	except:
		return 'Cannot find source file'

class BlockType:
    '''Sort of like an enum, but we literally want the int values and we don't
need any of the features we get from extending a Python Enum.
'''
    air="AIR"
    stone="STONE"
    granite="GRANITE"
    polished_granite="POLISHED_GRANITE"
    diorite="DIORITE"
    polished_diorite="POLISHED_DIORITE"
    andesite="ANDESITE"
    polished_andesite="POLISHED_ANDESITE"
    grass="GRASS"
    dirt="DIRT"
    coarse_dirt="COARSE_DIRT"
    podzol="PODZOL"
    cobblestone="COBBLESTONE"
    oak_wood_plank="OAK_WOOD_PLANK"
    spruce_wood_plank="SPRUCE_WOOD_PLANK"
    birch_wood_plank="BIRCH_WOOD_PLANK"
    jungle_wood_plank="JUNGLE_WOOD_PLANK"
    acacia_wood_plank="ACACIA_WOOD_PLANK"
    dark_oak_wood_plank="DARK_OAK_WOOD_PLANK"
    oak_sapling="OAK_SAPLING"
    spruce_sapling="SPRUCE_SAPLING"
    birch_sapling="BIRCH_SAPLING"
    jungle_sapling="JUNGLE_SAPLING"
    acacia_sapling="ACACIA_SAPLING"
    dark_oak_sapling="DARK_OAK_SAPLING"
    bedrock="BEDROCK"
    flowing_water="FLOWING_WATER"
    still_water="STILL_WATER"
    flowing_lava="FLOWING_LAVA"
    still_lava="STILL_LAVA"
    sand="SAND"
    red_sand="RED_SAND"
    gravel="GRAVEL"
    gold_ore="GOLD_ORE"
    iron_ore="IRON_ORE"
    coal_ore="COAL_ORE"
    oak_wood="OAK_WOOD"
    spruce_wood="SPRUCE_WOOD"
    birch_wood="BIRCH_WOOD"
    jungle_wood="JUNGLE_WOOD"
    oak_leaves="OAK_LEAVES"
    spruce_leaves="SPRUCE_LEAVES"
    birch_leaves="BIRCH_LEAVES"
    jungle_leaves="JUNGLE_LEAVES"
    sponge="SPONGE"
    wet_sponge="WET_SPONGE"
    glass="GLASS"
    lapis_lazuli_ore="LAPIS_LAZULI_ORE"
    lapis_lazuli_block="LAPIS_LAZULI_BLOCK"
    dispenser="DISPENSER"
    sandstone="SANDSTONE"
    chiseled_sandstone="CHISELED_SANDSTONE"
    smooth_sandstone="SMOOTH_SANDSTONE"
    note_block="NOTE_BLOCK"
    bed="BED"
    powered_rail="POWERED_RAIL"
    detector_rail="DETECTOR_RAIL"
    sticky_piston="STICKY_PISTON"
    cobweb="COBWEB"
    dead_shrub="DEAD_SHRUB"
    grass_tallgrass="GRASS_TALLGRASS"
    fern="FERN"
    dead_bush="DEAD_BUSH"
    piston="PISTON"
    piston_head="PISTON_HEAD"
    white_wool="WHITE_WOOL"
    orange_wool="ORANGE_WOOL"
    magenta_wool="MAGENTA_WOOL"
    light_blue_wool="LIGHT_BLUE_WOOL"
    yellow_wool="YELLOW_WOOL"
    lime_wool="LIME_WOOL"
    pink_wool="PINK_WOOL"
    gray_wool="GRAY_WOOL"
    light_gray_wool="LIGHT_GRAY_WOOL"
    cyan_wool="CYAN_WOOL"
    purple_wool="PURPLE_WOOL"
    blue_wool="BLUE_WOOL"
    brown_wool="BROWN_WOOL"
    green_wool="GREEN_WOOL"
    red_wool="RED_WOOL"
    black_wool="BLACK_WOOL"
    dandelion="DANDELION"
    poppy="POPPY"
    blue_orchid="BLUE_ORCHID"
    allium="ALLIUM"
    azure_bluet="AZURE_BLUET"
    red_tulip="RED_TULIP"
    orange_tulip="ORANGE_TULIP"
    white_tulip="WHITE_TULIP"
    pink_tulip="PINK_TULIP"
    oxeye_daisy="OXEYE_DAISY"
    brown_mushroom="BROWN_MUSHROOM"
    red_mushroom="RED_MUSHROOM"
    gold_block="GOLD_BLOCK"
    iron_block="IRON_BLOCK"
    double_stone_slab="DOUBLE_STONE_SLAB"
    double_sandstone_slab="DOUBLE_SANDSTONE_SLAB"
    double_wooden_slab="DOUBLE_WOODEN_SLAB"
    double_cobblestone_slab="DOUBLE_COBBLESTONE_SLAB"
    double_brick_slab="DOUBLE_BRICK_SLAB"
    double_stone_brick_slab="DOUBLE_STONE_BRICK_SLAB"
    double_nether_brick_slab="DOUBLE_NETHER_BRICK_SLAB"
    double_quartz_slab="DOUBLE_QUARTZ_SLAB"
    stone_slab="STONE_SLAB"
    sandstone_slab="SANDSTONE_SLAB"
    wooden_slab="WOODEN_SLAB"
    cobblestone_slab="COBBLESTONE_SLAB"
    brick_slab="BRICK_SLAB"
    stone_brick_slab="STONE_BRICK_SLAB"
    nether_brick_slab="NETHER_BRICK_SLAB"
    quartz_slab="QUARTZ_SLAB"
    bricks="BRICKS"
    tnt="TNT"
    bookshelf="BOOKSHELF"
    moss_stone="MOSS_STONE"
    obsidian="OBSIDIAN"
    torch="TORCH"
    fire="FIRE"
    monster_spawner="MONSTER_SPAWNER"
    oak_wood_stairs="OAK_WOOD_STAIRS"
    chest="CHEST"
    redstone_wire="REDSTONE_WIRE"
    diamond_ore="DIAMOND_ORE"
    diamond_block="DIAMOND_BLOCK"
    crafting_table="CRAFTING_TABLE"
    wheat_crops="WHEAT_CROPS"
    farmland="FARMLAND"
    furnace="FURNACE"
    burning_furnace="BURNING_FURNACE"
    standing_sign_block="STANDING_SIGN_BLOCK"
    oak_door_block="OAK_DOOR_BLOCK"
    ladder="LADDER"
    rail="RAIL"
    cobblestone_stairs="COBBLESTONE_STAIRS"
    wall_mounted_sign_block="WALL_MOUNTED_SIGN_BLOCK"
    lever="LEVER"
    stone_pressure_plate="STONE_PRESSURE_PLATE"
    iron_door_block="IRON_DOOR_BLOCK"
    wooden_pressure_plate="WOODEN_PRESSURE_PLATE"
    redstone_ore="REDSTONE_ORE"
    glowing_redstone_ore="GLOWING_REDSTONE_ORE"
    redstone_torch_off="REDSTONE_TORCH_OFF"
    redstone_torch_on="REDSTONE_TORCH_ON"
    stone_button="STONE_BUTTON"
    snow="SNOW"
    ice="ICE"
    snow_block="SNOW_BLOCK"
    cactus="CACTUS"
    clay="CLAY"
    sugar_canes="SUGAR_CANES"
    jukebox="JUKEBOX"
    oak_fence="OAK_FENCE"
    pumpkin="PUMPKIN"
    netherrack="NETHERRACK"
    soul_sand="SOUL_SAND"
    glowstone="GLOWSTONE"
    nether_portal="NETHER_PORTAL"
    jack_olantern="JACK_OLANTERN"
    cake_block="CAKE_BLOCK"
    redstone_repeater_block_off="REDSTONE_REPEATER_BLOCK_OFF"
    redstone_repeater_block_on="REDSTONE_REPEATER_BLOCK_ON"
    white_stained_glass="WHITE_STAINED_GLASS"
    orange_stained_glass="ORANGE_STAINED_GLASS"
    magenta_stained_glass="MAGENTA_STAINED_GLASS"
    light_blue_stained_glass="LIGHT_BLUE_STAINED_GLASS"
    yellow_stained_glass="YELLOW_STAINED_GLASS"
    lime_stained_glass="LIME_STAINED_GLASS"
    pink_stained_glass="PINK_STAINED_GLASS"
    gray_stained_glass="GRAY_STAINED_GLASS"
    light_gray_stained_glass="LIGHT_GRAY_STAINED_GLASS"
    cyan_stained_glass="CYAN_STAINED_GLASS"
    purple_stained_glass="PURPLE_STAINED_GLASS"
    blue_stained_glass="BLUE_STAINED_GLASS"
    brown_stained_glass="BROWN_STAINED_GLASS"
    green_stained_glass="GREEN_STAINED_GLASS"
    red_stained_glass="RED_STAINED_GLASS"
    black_stained_glass="BLACK_STAINED_GLASS"
    wooden_trapdoor="WOODEN_TRAPDOOR"
    stone_monster_egg="STONE_MONSTER_EGG"
    cobblestone_monster_egg="COBBLESTONE_MONSTER_EGG"
    stone_brick_monster_egg="STONE_BRICK_MONSTER_EGG"
    mossy_stone_brick_monster_egg="MOSSY_STONE_BRICK_MONSTER_EGG"
    cracked_stone_brick_monster_egg="CRACKED_STONE_BRICK_MONSTER_EGG"
    chiseled_stone_brick_monster_egg="CHISELED_STONE_BRICK_MONSTER_EGG"
    stone_bricks="STONE_BRICKS"
    mossy_stone_bricks="MOSSY_STONE_BRICKS"
    cracked_stone_bricks="CRACKED_STONE_BRICKS"
    chiseled_stone_bricks="CHISELED_STONE_BRICKS"
    brown_mushroom_block="BROWN_MUSHROOM_BLOCK"
    red_mushroom_block="RED_MUSHROOM_BLOCK"
    iron_bars="IRON_BARS"
    glass_pane="GLASS_PANE"
    melon_block="MELON_BLOCK"
    pumpkin_stem="PUMPKIN_STEM"
    melon_stem="MELON_STEM"
    vines="VINES"
    oak_fence_gate="OAK_FENCE_GATE"
    brick_stairs="BRICK_STAIRS"
    stone_brick_stairs="STONE_BRICK_STAIRS"
    mycelium="MYCELIUM"
    lily_pad="LILY_PAD"
    nether_brick="NETHER_BRICK"
    nether_brick_fence="NETHER_BRICK_FENCE"
    nether_brick_stairs="NETHER_BRICK_STAIRS"
    nether_wart="NETHER_WART"
    enchantment_table="ENCHANTMENT_TABLE"
    brewing_stand="BREWING_STAND"
    cauldron="CAULDRON"
    end_portal="END_PORTAL"
    end_portal_frame="END_PORTAL_FRAME"
    end_stone="END_STONE"
    dragon_egg="DRAGON_EGG"
    redstone_lamp_inactive="REDSTONE_LAMP_INACTIVE"
    redstone_lamp_active="REDSTONE_LAMP_ACTIVE"
    double_oak_wood_slab="DOUBLE_OAK_WOOD_SLAB"
    double_spruce_wood_slab="DOUBLE_SPRUCE_WOOD_SLAB"
    double_birch_wood_slab="DOUBLE_BIRCH_WOOD_SLAB"
    double_jungle_wood_slab="DOUBLE_JUNGLE_WOOD_SLAB"
    double_acacia_wood_slab="DOUBLE_ACACIA_WOOD_SLAB"
    double_dark_oak_wood_slab="DOUBLE_DARK_OAK_WOOD_SLAB"
    oak_wood_slab="OAK_WOOD_SLAB"
    spruce_wood_slab="SPRUCE_WOOD_SLAB"
    birch_wood_slab="BIRCH_WOOD_SLAB"
    jungle_wood_slab="JUNGLE_WOOD_SLAB"
    acacia_wood_slab="ACACIA_WOOD_SLAB"
    dark_oak_wood_slab="DARK_OAK_WOOD_SLAB"
    cocoa="COCOA"
    sandstone_stairs="SANDSTONE_STAIRS"
    emerald_ore="EMERALD_ORE"
    ender_chest="ENDER_CHEST"
    tripwire_hook="TRIPWIRE_HOOK"
    tripwire="TRIPWIRE"
    emerald_block="EMERALD_BLOCK"
    spruce_wood_stairs="SPRUCE_WOOD_STAIRS"
    birch_wood_stairs="BIRCH_WOOD_STAIRS"
    jungle_wood_stairs="JUNGLE_WOOD_STAIRS"
    command_block="COMMAND_BLOCK"
    beacon="BEACON"
    cobblestone_wall="COBBLESTONE_WALL"
    mossy_cobblestone_wall="MOSSY_COBBLESTONE_WALL"
    flower_pot="FLOWER_POT"
    carrots="CARROTS"
    potatoes="POTATOES"
    wooden_button="WOODEN_BUTTON"
    mob_head="MOB_HEAD"
    anvil="ANVIL"
    trapped_chest="TRAPPED_CHEST"
    weighted_pressure_plate_light="WEIGHTED_PRESSURE_PLATE_LIGHT"
    weighted_pressure_plate_heavy="WEIGHTED_PRESSURE_PLATE_HEAVY"
    redstone_comparator_inactive="REDSTONE_COMPARATOR_INACTIVE"
    redstone_comparator_active="REDSTONE_COMPARATOR_ACTIVE"
    daylight_sensor="DAYLIGHT_SENSOR"
    redstone_block="REDSTONE_BLOCK"
    nether_quartz_ore="NETHER_QUARTZ_ORE"
    hopper="HOPPER"
    quartz_block="QUARTZ_BLOCK"
    chiseled_quartz_block="CHISELED_QUARTZ_BLOCK"
    pillar_quartz_block="PILLAR_QUARTZ_BLOCK"
    quartz_stairs="QUARTZ_STAIRS"
    activator_rail="ACTIVATOR_RAIL"
    dropper="DROPPER"
    white_stained_clay="WHITE_STAINED_CLAY"
    orange_stained_clay="ORANGE_STAINED_CLAY"
    magenta_stained_clay="MAGENTA_STAINED_CLAY"
    light_blue_stained_clay="LIGHT_BLUE_STAINED_CLAY"
    yellow_stained_clay="YELLOW_STAINED_CLAY"
    lime_stained_clay="LIME_STAINED_CLAY"
    pink_stained_clay="PINK_STAINED_CLAY"
    gray_stained_clay="GRAY_STAINED_CLAY"
    light_gray_stained_clay="LIGHT_GRAY_STAINED_CLAY"
    cyan_stained_clay="CYAN_STAINED_CLAY"
    purple_stained_clay="PURPLE_STAINED_CLAY"
    blue_stained_clay="BLUE_STAINED_CLAY"
    brown_stained_clay="BROWN_STAINED_CLAY"
    green_stained_clay="GREEN_STAINED_CLAY"
    red_stained_clay="RED_STAINED_CLAY"
    black_stained_clay="BLACK_STAINED_CLAY"
    white_stained_glass_pane="WHITE_STAINED_GLASS_PANE"
    orange_stained_glass_pane="ORANGE_STAINED_GLASS_PANE"
    magenta_stained_glass_pane="MAGENTA_STAINED_GLASS_PANE"
    light_blue_stained_glass_pane="LIGHT_BLUE_STAINED_GLASS_PANE"
    yellow_stained_glass_pane="YELLOW_STAINED_GLASS_PANE"
    lime_stained_glass_pane="LIME_STAINED_GLASS_PANE"
    pink_stained_glass_pane="PINK_STAINED_GLASS_PANE"
    gray_stained_glass_pane="GRAY_STAINED_GLASS_PANE"
    light_gray_stained_glass_pane="LIGHT_GRAY_STAINED_GLASS_PANE"
    cyan_stained_glass_pane="CYAN_STAINED_GLASS_PANE"
    purple_stained_glass_pane="PURPLE_STAINED_GLASS_PANE"
    blue_stained_glass_pane="BLUE_STAINED_GLASS_PANE"
    brown_stained_glass_pane="BROWN_STAINED_GLASS_PANE"
    green_stained_glass_pane="GREEN_STAINED_GLASS_PANE"
    red_stained_glass_pane="RED_STAINED_GLASS_PANE"
    black_stained_glass_pane="BLACK_STAINED_GLASS_PANE"
    acacia_leaves="ACACIA_LEAVES"
    dark_oak_leaves="DARK_OAK_LEAVES"
    acacia_wood="ACACIA_WOOD"
    dark_oak_wood="DARK_OAK_WOOD"
    acacia_wood_stairs="ACACIA_WOOD_STAIRS"
    dark_oak_wood_stairs="DARK_OAK_WOOD_STAIRS"
    slime_block="SLIME_BLOCK"
    barrier="BARRIER"
    iron_trapdoor="IRON_TRAPDOOR"
    prismarine="PRISMARINE"
    prismarine_bricks="PRISMARINE_BRICKS"
    dark_prismarine="DARK_PRISMARINE"
    sea_lantern="SEA_LANTERN"
    hay_bale="HAY_BALE"
    white_carpet="WHITE_CARPET"
    orange_carpet="ORANGE_CARPET"
    magenta_carpet="MAGENTA_CARPET"
    light_blue_carpet="LIGHT_BLUE_CARPET"
    yellow_carpet="YELLOW_CARPET"
    lime_carpet="LIME_CARPET"
    pink_carpet="PINK_CARPET"
    gray_carpet="GRAY_CARPET"
    light_gray_carpet="LIGHT_GRAY_CARPET"
    cyan_carpet="CYAN_CARPET"
    purple_carpet="PURPLE_CARPET"
    blue_carpet="BLUE_CARPET"
    brown_carpet="BROWN_CARPET"
    green_carpet="GREEN_CARPET"
    red_carpet="RED_CARPET"
    black_carpet="BLACK_CARPET"
    hardened_clay="HARDENED_CLAY"
    block_of_coal="BLOCK_OF_COAL"
    packed_ice="PACKED_ICE"
    sunflower="SUNFLOWER"
    lilac="LILAC"
    double_tallgrass="DOUBLE_TALLGRASS"
    large_fern="LARGE_FERN"
    rose_bush="ROSE_BUSH"
    peony="PEONY"
    free_standing_banner="FREE_STANDING_BANNER"
    wall_mounted_banner="WALL_MOUNTED_BANNER"
    inverted_daylight_sensor="INVERTED_DAYLIGHT_SENSOR"
    red_sandstone="RED_SANDSTONE"
    chiseled_red_sandstone="CHISELED_RED_SANDSTONE"
    smooth_red_sandstone="SMOOTH_RED_SANDSTONE"
    red_sandstone_stairs="RED_SANDSTONE_STAIRS"
    double_red_sandstone_slab="DOUBLE_RED_SANDSTONE_SLAB"
    red_sandstone_slab="RED_SANDSTONE_SLAB"
    spruce_fence_gate="SPRUCE_FENCE_GATE"
    birch_fence_gate="BIRCH_FENCE_GATE"
    jungle_fence_gate="JUNGLE_FENCE_GATE"
    dark_oak_fence_gate="DARK_OAK_FENCE_GATE"
    acacia_fence_gate="ACACIA_FENCE_GATE"
    spruce_fence="SPRUCE_FENCE"
    birch_fence="BIRCH_FENCE"
    jungle_fence="JUNGLE_FENCE"
    dark_oak_fence="DARK_OAK_FENCE"
    acacia_fence="ACACIA_FENCE"
    spruce_door_block="SPRUCE_DOOR_BLOCK"
    birch_door_block="BIRCH_DOOR_BLOCK"
    jungle_door_block="JUNGLE_DOOR_BLOCK"
    acacia_door_block="ACACIA_DOOR_BLOCK"
    dark_oak_door_block="DARK_OAK_DOOR_BLOCK"
    end_rod="END_ROD"
    chorus_plant="CHORUS_PLANT"
    chorus_flower="CHORUS_FLOWER"
    purpur_block="PURPUR_BLOCK"
    purpur_pillar="PURPUR_PILLAR"
    purpur_stairs="PURPUR_STAIRS"
    purpur_double_slab="PURPUR_DOUBLE_SLAB"
    purpur_slab="PURPUR_SLAB"
    end_stone_bricks="END_STONE_BRICKS"
    beetroot_block="BEETROOT_BLOCK"
    grass_path="GRASS_PATH"
    end_gateway="END_GATEWAY"
    repeating_command_block="REPEATING_COMMAND_BLOCK"
    chain_command_block="CHAIN_COMMAND_BLOCK"
    frosted_ice="FROSTED_ICE"
    structure_block="STRUCTURE_BLOCK"
