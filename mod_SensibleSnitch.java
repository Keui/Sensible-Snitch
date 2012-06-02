package net.minecraft.src;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;

public class mod_SensibleSnitch extends BaseMod {

	public static class MyWaypoint {
		public String name;
		public int x, y, z;

		public MyWaypoint(Object o) {
			x = (Integer)getField(waypointClass, o, "x");
			y = (Integer)getField(waypointClass, o, "y");
			z = (Integer)getField(waypointClass, o, "z");
			name = (String)getField(waypointClass, o, "name");
		}

		public MyWaypoint(int x, int y, int z, String name)
		{
			this.x = x;
			this.y = y;
			this.z = z;
			this.name = name;
		}
		static MyWaypoint load(String line)
		{
			try
			{
				String[] elements = line.split(":");
				String name = elements[0];
				int x = Integer.parseInt(elements[1]);
				int y = Integer.parseInt(elements[2]);
				int z = Integer.parseInt(elements[3]);
				return new MyWaypoint(x, y, z, name);
			} catch (Exception e) {

			}
			return null;
		}
	}

	@Override
	public String getVersion() {
		// TODO Auto-generated method stub
		return "0.1 Sensible Snitch";
	}

	@Override
	public void load() {
		ModLoader.setInGameHook(this, true, true);
	}
	String entryAlert = " has triggered an entry alert at ";
	String nether = "world_nether";
	String packetInAction = null;
	@Override
	public void receiveChatPacket(String s)
	{
		s = strip(s);
		try {
			if (packetInAction != null) s = packetInAction + " " + s;
			if (!s.contains(entryAlert)) return;
			if (s.indexOf("]") == -1 && s.startsWith(" * ")) {
				packetInAction = strip(s);
				removeLastChat();
				return;
			} else if (s.startsWith(" * ")) {
				removeLastChat();
			} else {
				return;
			}
			String[] ss = s.split(entryAlert);
			String name = ss[0].substring(ss[0].lastIndexOf(" * ") + 3);
			String place = ss[1];
			System.out.println(s);
			System.out.println(place);
			String[] nums = ss[1].substring(ss[1].indexOf("[") + 1).split(" ");
			int[] numsFound = new int[3];
			int i = 0;
			int dim = 0;
			if (nums.length >= 4)
			{
				numsFound[0] = Integer.parseInt(nums[0]);
				numsFound[1] = Integer.parseInt(nums[1]);
				numsFound[2] = Integer.parseInt(nums[2]);
				if (nums[3].substring(0, nums[3].indexOf("]")).indexOf(nether) != -1) dim = 1;
			} else {
				ingameGui.addChatMessage("Failed to parse entry alert:" + s);
				for (String ns : nums)
					System.out.println(i++ + " " + ns);
				System.out.println(s.indexOf("]"));
				return;
			}
			String findPlace = tryFindWaypoint(numsFound[0], numsFound[1], numsFound[2], dim);
			ingameGui.addChatMessage("§E" + name + " is at " + (findPlace != "" ? findPlace : place) + (dim == 0 ? " in overworld." : " in nether."));
			packetInAction = null;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void removeLastChat() {
		try {
			Field f = ingameGui.getClass().getDeclaredField("e");
			if (!f.isAccessible())
				f.setAccessible(true);
			System.out.println("Removing " + ((ChatLine)((List)f.get(ingameGui)).remove(0)).message);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			System.out.println(ingameGui.getClass().getDeclaredFields());
			e.printStackTrace();
		}
	}

	private String strip(String s) {

		StringBuilder sb = new StringBuilder();
		int i = 0, c = 0;
		while ((c = s.indexOf('§', i)) >= 0) {
			sb.append(s.substring(i,c));
			i = c + 2;
		}
		sb.append(s.substring(i));
		return sb.toString();
	}
	private String extraClassPath = "minimap.reifnsk.minimap";
	boolean reisCheck = false;
	boolean reisFound = false;
	private String tryFindWaypoint(int x, int y, int z, int dim) {
		if (!reisCheck) checkForReis();
		if (reisFound) {
			List<MyWaypoint> points = getMyWaypoints(dim);
			double distance = -1;
			String name = "";
			for (MyWaypoint p : points) {
				int cx = p.x - x;
				int cy = p.y - y;
				int cz = p.z - z;
				double d = Math.sqrt(cx * cx + cy * cy + cz * cz);
				if (distance == -1 || d < distance) {
					name = p.name;
					distance = d;
				}
			}
			return name;
		}
		return "";
	}
	static Class minimapClass;
	private void checkForReis() {
		reisCheck = true;
		try {
			minimapClass = ModLoader.class.getClassLoader().loadClass("net.minecraft.src.reifnsk.minimap.ReiMinimap");
		} catch (ClassNotFoundException cnfe) {
			try {
				minimapClass = ModLoader.class.getClassLoader().loadClass("reifnsk.minimap.ReiMinimap");
			} catch (ClassNotFoundException cnfe2) {
				System.out.println("Reis not found.");
				return;
			}
		}
		loadReisWaypoints();
		reisFound = true;
	}

	private List<MyWaypoint> getMyWaypoints(int dim) {
		if (wayPointArray[dim] != null) return wayPointArray[dim];
		else return new ArrayList<MyWaypoint>();
	}

	int currentDimension = -1, lastDimension = -1;
	GuiIngame ingameGui;
	public boolean onTickInGame(float gg, Minecraft minecraft)
	{
		if (ingameGui == null) ingameGui = minecraft.ingameGUI;
		currentDimension = minecraft.thePlayer.dimension;
		if (currentLevelName == null) levelName(minecraft);
		if (reisFound) {
			updateReis();
		}
		return true;
	}
	String currentLevelName = null;
	private String levelName(Minecraft minecraft) { 

		if (minecraft.theWorld != null)
		{
			String levelName = null;

			SocketAddress addr = getRemoteSocketAddress(minecraft.thePlayer);

			if (addr == null) return null;

			String addrStr = addr.toString().replaceAll("[\r\n]", "");
			Matcher matcher = Pattern.compile("(.*)/(.*):([0-9]+)").matcher(addrStr);
			if (matcher.matches())
			{
				levelName = matcher.group(1);
				if (levelName.isEmpty())
				{
					levelName = matcher.group(2);
				}
				if (!matcher.group(3).equals("25565"))
				{
					levelName = new StringBuilder().append(levelName).append("[").append(matcher.group(3)).append("]").toString();
				}
			}
			else {
				String str = addr.toString().replaceAll("[a-z]", "a").replaceAll("[A-Z]", "A").replaceAll("[0-9]", "*");
			}
			return currentLevelName = levelName;
		}
		System.out.println("Failed to get level name");
		return null;
	}
	List<MyWaypoint>[] wayPointArray = new List[3];
	File directory = new File(Minecraft.getMinecraftDir(), "mods" + File.separatorChar + "rei_minimap");
	private void loadReisWaypoints() {
		Pattern pattern = Pattern.compile(new StringBuilder().append(Pattern.quote(this.currentLevelName)).append("\\.DIM(-?[0-9])\\.points").toString());

		int load = 0;
		int dim = 0;
		for (String file : directory.list())
		{
			Matcher m = pattern.matcher(file);
			if (!m.matches())
				continue;
			dim++;
			int dimension = Integer.parseInt(m.group(1));
			ArrayList list = new ArrayList();
			Scanner in = null;
			try
			{
				in = new Scanner(new File(directory, file), "UTF-8");
				while (in.hasNextLine())
				{
					MyWaypoint wp = MyWaypoint.load(in.nextLine());
					if (wp != null)
					{
						list.add(wp);
						load++;
					}
				}
			}
			catch (Exception e) {
			}
			finally {
				if (in != null) in.close();
			}

			wayPointArray[Integer.valueOf(-dimension)] = list;
		}
	}
	int lastLength = -1;
	static Class waypointClass;
	private void updateReis() {
		if (waypointClass == null) {
			try {
				waypointClass = mod_SensibleSnitch.class.getClassLoader().loadClass("net.minecraft.src.reifnsk.minimap.Waypoint");
			} catch (ClassNotFoundException cnfe) { 
				try {
					waypointClass = mod_SensibleSnitch.class.getClassLoader().loadClass("reifnsk.minimap.Waypoint");
				} catch (ClassNotFoundException cnfe2) {
					return;
				}
			}
		}
		if (lastDimension != currentDimension || lastLength != reiWaypoints().size()) {
			System.out.println("Updating from reis'");
			List temp = reiWaypoints();
			List<MyWaypoint> myTemp = new ArrayList<MyWaypoint>();
			for (Object p : temp) myTemp.add(new MyWaypoint(p));
			wayPointArray[-currentDimension] = myTemp;
			lastDimension = currentDimension;
			lastLength = reiWaypoints().size();
		}
	}

	private List reiWaypoints() {
		Object instance = getField(minimapClass, minimapClass,"instance");
		try {
			return (List)(minimapClass.getDeclaredMethod("getWaypoints").invoke(instance));
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private static Object getField(Class clazz, Object obj, String name)
	{
		while (clazz != null)
		{
			try
			{
				Field field = clazz.getDeclaredField(name);
				field.setAccessible(true);
				return field.get(obj);
			}
			catch (Exception e) {
			}
			clazz = clazz.getSuperclass();
		}
		return null;
	}

	private static SocketAddress getRemoteSocketAddress(EntityPlayerSP player)
	{
		if ((player instanceof EntityClientPlayerMP))
		{
			try
			{
				NetClientHandler netClientHandler = ((EntityClientPlayerMP)player).sendQueue;
				NetworkManager networkManager = null;
				for (Field field : NetClientHandler.class.getDeclaredFields())
				{
					if (field.getType() != NetworkManager.class)
						continue;
					field.setAccessible(true);
					networkManager = (NetworkManager)field.get(netClientHandler);
					break;
				}

				if (networkManager == null) return null;

				for (Field field : NetworkManager.class.getDeclaredFields())
				{
					if (field.getType() != SocketAddress.class)
						continue;
					field.setAccessible(true);
					return (SocketAddress)field.get(networkManager);
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}

		return null;
	}
}
