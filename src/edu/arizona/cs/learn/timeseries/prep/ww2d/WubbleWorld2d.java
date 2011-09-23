package edu.arizona.cs.learn.timeseries.prep.ww2d;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.arizona.cs.learn.timeseries.model.Interval;
import edu.arizona.cs.learn.timeseries.prep.TimeSeries;


/**
 * The purpose of this class is to take in the raw ww2d data and
 * convert it into some set of propositions.  This is not a blind
 * procedure as it has been before.  In this case, we are interested in
 * certain relations and we'll try to find them.
 * @author wkerr, Anh Tran
 *
 */
public class WubbleWorld2d {
	
	public static enum DBType {
		Global,
		Agent,
		Object
	}
	
	public static double ZERO = 0.01;
	
	private boolean _ignoreWalls;
	
	private Map<DBType,List<String>> _headers = new HashMap<WubbleWorld2d.DBType, List<String>>();
	private Map<DBType, Map<String,List<Double>>> _doubleMap = new HashMap<WubbleWorld2d.DBType, Map<String,List<Double>>>();
	private Map<DBType, Map<String,List<String>>> _stringMap = new HashMap<WubbleWorld2d.DBType, Map<String,List<String>>>();
	private Map<DBType, Map<String,List<Boolean>>> _booleanMap = new HashMap<WubbleWorld2d.DBType, Map<String,List<Boolean>>>();
	private Map<DBType, List<Interval>> _intervalMap = new HashMap<WubbleWorld2d.DBType, List<Interval>>();
	
	public WubbleWorld2d(boolean ignoreWalls) { 
		_ignoreWalls = ignoreWalls;
	}
	
	/**
	 * 
	 * @param input
	 * @param dType
	 */
	public void load(String input, DBType dType) {
		_headers.put(dType, new ArrayList<String>());
		Map<String,List<String>> map = new HashMap<String,List<String>>();

		try { 
			BufferedReader in = new BufferedReader(new FileReader(input));
			
			String line = in.readLine();
			String[] tokens = line.split("[,]");
				
			for (String token : tokens) {
				String header = token.replaceAll("[\"]", "");
				_headers.get(dType).add(header);
				map.put(header, new ArrayList<String>());
			}
			
			while (in.ready()) { 
				line = in.readLine();
				tokens = line.split("[,]");
				for (int i = 0; i < tokens.length; ++i) 
					map.get(_headers.get(dType).get(i)).add(tokens[i]);
			}
			in.close();
		} catch (Exception e) { 
			e.printStackTrace();
		}

		_doubleMap.put(dType, new HashMap<String,List<Double>>());
		_stringMap.put(dType, new HashMap<String,List<String>>());
		_booleanMap.put(dType, new HashMap<String,List<Boolean>>());
		_intervalMap.put(dType, new ArrayList<Interval>());
		
		// Now iterate through all of the possible columns and 
		// partition them into the correct sets.
		for (String key : _headers.get(dType)) { 
			
			// determine the type....
			// Unfortunately, the only way to robustly determine the type is to go
			// through the entire list.
			boolean seeUnknown, seeBoolean, seeDouble, seeOthers;
			seeUnknown = seeBoolean = seeDouble = seeOthers = false;
			for (String s : map.get(key)) {
				if (s.equalsIgnoreCase("unknown")) {
					continue;
				} else if ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) {
					seeBoolean = true;
				} else {
					try {
						Double.parseDouble(s);
						seeDouble = true;
					} catch (Exception e) {
						seeOthers = true;
					}
				}
			}
			
			// Add the data to the appropriate map
			if (seeBoolean && !seeUnknown && !seeDouble && !seeOthers) {
				// Boolean
				List<Boolean> list = new ArrayList<Boolean>();
				for (String value : map.get(key)) { 
					list.add(Boolean.parseBoolean(value));
				}
				_booleanMap.get(dType).put(key, list);
			} else if (seeDouble && !seeBoolean && !seeOthers) {
				// Double with possible unknown
				try {
					List<Double> list = new ArrayList<Double>();
					for (String value : map.get(key)) { 
						if (value.equalsIgnoreCase("unknown"))
							list.add(Double.NaN);
						else
							list.add(Double.parseDouble(value));
					}
					_doubleMap.get(dType).put(key, list);
				} catch (Exception e) { 
					throw new RuntimeException("Bad double value.");
				}
			} else {
				// String
				_stringMap.get(dType).put(key, map.get(key));
			}
		}
	}
	
	/**
	 * Take an array of strings and convert them into a map of boolean values
	 * @param values
	 * @return
	 */
	private Map<String,List<Boolean>> convert(List<String> values) { 
		Map<String,List<Boolean>> map = new HashMap<String,List<Boolean>>();
		for (String s : values) { 
			// if (!map.containsKey(s) && !s.equals("NaN"))
			// We're going to allow NaN, and we'll treat it as
			// an 'unknown' class.
			if (!map.containsKey(s))
				map.put(s, new ArrayList<Boolean>(values.size()));
		}

		for (String s : values) { 
			for (String key : map.keySet()) { 
				if (s.equals(key))
					map.get(key).add(true);
				else
					map.get(key).add(false);
			}
		}
		return map;
	}
	
	/**
	 * 
	 * @param dType
	 * @param prefixes
	 * @param mapped
	 */
	public void doBooleanStream(DBType dType, String[] prefixes, String[] mapped) {
		if (prefixes == null)	// if no prefixes, then do all
			prefixes = _booleanMap.get(dType).keySet().toArray(new String[0]);
		if (mapped == null)
			mapped = prefixes;
		for (int i = 0; i < prefixes.length; i++) {
			String prefix = prefixes[i];
			for (String key : _booleanMap.get(dType).keySet()) {
				if (!key.startsWith(prefix)) 
					continue;
				
				if (_ignoreWalls && key.matches(".*wall.*"))
					continue;

				String suffix = key.substring(prefix.length());
				String s = (i < mapped.length) ? mapped[i] + suffix : key;
				_intervalMap.get(dType).addAll(TimeSeries.booleanToIntervals(s, _booleanMap.get(dType).get(key)));
			}
		}
	}

	/**
	 * 
	 * @param dType
	 * @param prefixes - Non-empty list of prefixes
	 * @param mapped
	 */
	public void doStringStream(DBType dType, String[] prefixes, String[] mapped) {
		if (mapped == null)
			mapped = prefixes;
		for (int i = 0; i < prefixes.length; i++) {
			String prefix = prefixes[i];
			for (String key : _stringMap.get(dType).keySet()) {
				if (!key.startsWith(prefix)) 
					continue;
				
				if (_ignoreWalls && key.matches(".*wall.*"))
					continue;

				String suffix = key.substring(prefix.length());
				Map<String,List<Boolean>> strMap = convert(_stringMap.get(dType).get(key));
				
				for (String symbol : strMap.keySet()) { 
					String pref = (i < mapped.length) ? mapped[i] : prefix;
					String s = pref + "-" + symbol + suffix;
					_booleanMap.get(dType).put(s, strMap.get(symbol));
					_intervalMap.get(dType).addAll(TimeSeries.booleanToIntervals(s, strMap.get(symbol)));
				}
			}
		}
	}
	
	/**
	 * 
	 * @param dType
	 * @param prefixes - Non-empty list of prefixes.
	 * @param mapped
	 */
	public void doSDL(DBType dType, String[] prefixes, String[] mapped) {
		if (mapped == null)		// if no mapped, use same as prefixes
			mapped = prefixes;
		for (int i = 0; i < prefixes.length; ++i) { 
			String prefix = prefixes[i];
			for (String key : _doubleMap.get(dType).keySet()) {
				if (!key.startsWith(prefix)) 
					continue;
				
				if (_ignoreWalls && key.matches(".*wall.*"))
					continue;
				
				String suffix = key.substring(prefix.length());
				System.out.println(suffix);
				
				// assumption is that it should be populated in the double map.
				List<Double> values = _doubleMap.get(dType).get(key);
				if (values == null) { 
					throw new RuntimeException("Unknown key: " + key);
				}

				List<String> classes = Arrays.asList("decreasing", "stable", "increasing");
				List<Double> diff = TimeSeries.diff(values);
				List<String> sdl = TimeSeries.sdl(diff, Arrays.asList(-ZERO,ZERO), classes);
				Map<String,List<Boolean>> sdlMap = convert(sdl);
				
				for (String symbol : sdlMap.keySet()) { 
					String pref = (i < mapped.length) ? mapped[i] : prefix;
					String s = pref + "-" + symbol + suffix;
					_booleanMap.get(dType).put(s, sdlMap.get(symbol));
					_intervalMap.get(dType).addAll(TimeSeries.booleanToIntervals(s, sdlMap.get(symbol)));
				}
			}
		}
	}
	
	/**
	 * 
	 * @param dType
	 * @param prefixes - Non-empty list of prefixes.
	 * @param mapped
	 * @param numBreaks
	 */
	public void doSAX(DBType dType, String[] prefixes, String[] mapped, int numBreaks) {
		if (mapped == null)		// if no mapped, use same as prefixes
			mapped = prefixes;
		for (int i = 0; i < prefixes.length; ++i) { 
			String prefix = prefixes[i];
			for (String key : _doubleMap.get(dType).keySet()) {
				if (!key.startsWith(prefix)) 
					continue;
				
				if (_ignoreWalls && key.matches(".*wall.*"))
					continue;
				
				String suffix = key.substring(prefix.length());
				System.out.println(suffix);
				
				// assumption is that it should be populated in the double map.
				List<Double> values = _doubleMap.get(dType).get(key);
				if (values == null) { 
					throw new RuntimeException("Unknown key: " + key);
				}
				
				List<Double> standard = TimeSeries.standardize(values);
				List<String> sax = TimeSeries.sax(standard, numBreaks);
				Map<String,List<Boolean>> saxMap = convert(sax);

				for (String symbol : saxMap.keySet()) { 
					String pref = (i < mapped.length) ? mapped[i] : prefix;
					String s = "(sax " + pref + suffix + " " + symbol + ")";
					_booleanMap.get(dType).put(s, saxMap.get(symbol));
					_intervalMap.get(dType).addAll(TimeSeries.booleanToIntervals(s, saxMap.get(symbol)));
				}
			}
		}
	}
	
	/**
	 * For each x,y pair, determine if the agent is moving.  This could be augmented
	 * to additionally have a movement in one of the axes, such as moving-y and 
	 * moving-x
	 *  -- Moving
	 *  -- MovingNaN
	 *  
	 *  Note: For now there is no smoothing going on.
	 */
	public void doMoving(DBType dType) { 
		Set<String> entities = new HashSet<String>();
		for (String key : _doubleMap.get(dType).keySet()) {
			if (key.startsWith("x(") || key.startsWith("y(")) { 
				entities.add(key.substring(1));
			}
		}
		
		for (String suffix : entities) {

			if (_ignoreWalls && suffix.matches(".*wall.*"))
				continue;
			
			List<Double> x = _doubleMap.get(dType).get("x" + suffix);
			List<Double> y = _doubleMap.get(dType).get("y" + suffix);
			
			List<Double> diffX = TimeSeries.diff(x);
			List<Double> diffY = TimeSeries.diff(y);

			List<Boolean> moving = new ArrayList<Boolean>();
			List<Boolean> movingNaN = new ArrayList<Boolean>();	// Equiv to moving-unknown	
			moving.add(false);
			movingNaN.add(false);
			
			for (int i = 1; i < x.size(); ++i) {
				if (Double.compare(Double.NaN, diffX.get(i)) == 0 && Double.compare(Double.NaN, diffY.get(i)) == 0) {
					moving.add(false);
					movingNaN.add(true);
				} else {
					movingNaN.add(false);
					if ( (Double.compare(Double.NaN, diffX.get(i)) != 0 && diffX.get(i) > ZERO || diffX.get(i) < -ZERO)
							|| (Double.compare(Double.NaN, diffY.get(i)) != 0 && diffY.get(i) > ZERO || diffY.get(i) < -ZERO) )
						moving.add(true);
					else
						moving.add(false);
				}
			}

			_booleanMap.get(dType).put("moving"+suffix, moving);
			_booleanMap.get(dType).put("moving-NaN"+suffix, movingNaN);
			_intervalMap.get(dType).addAll(TimeSeries.booleanToIntervals("moving"+suffix, moving));
			_intervalMap.get(dType).addAll(TimeSeries.booleanToIntervals("moving-NaN"+suffix, movingNaN));
		}
	}
	
	/**
	 * The following real-valued variables will be converted into propositions
	 * 		relativeVx, relativeVy
	 * 		relativeX, relativeY
	 */
	public void doRelative(DBType dType) { 
		String[] prefixes = new String[] { "relativeVx", "relativeVy", "relativeX", "relativeY" };
		String[] mapped = new String[] { "rvx", "rvy", "rx" ,"ry" };
		doSDL(dType, prefixes, mapped);
		doSAX(dType, prefixes, mapped, 5);
	}
	
	/**
	 * The following real-valued & string variables will be converted into propositions
	 * 		energy, arousal, valence, novel
	 * 		goal, state
	 */
	public void doInternalStates(DBType dType) { 
		String[] dblPrefixes = new String[] { "energy", "arousal", "valence", "novel" };
		String[] strPrefixes = new String[] { "goal", "state" };
		doSDL(dType, dblPrefixes, null);
		doSAX(dType, dblPrefixes, null, 3);
		doStringStream(dType, strPrefixes, null);
	}
	
	public List<Interval> getIntervals(DBType dType) { 
		return _intervalMap.get(dType);
	}

	public static void main(String[] args) { 
		String[] activities = {"chase", "eat", "fight", "flee", "kick-ball", "kick-column"};
		
//		String[] activities = {"eat"};

		global(30, activities, true);
	} 
	
	public static void global(int n, String[] activities, boolean ignoreWalls) { 
		WubbleWorld2d ww2d = new WubbleWorld2d(ignoreWalls);
		String prefix = "data/raw-data/ww2d/";

		for (String act : activities) { 
			try { 
				BufferedWriter out = new BufferedWriter(new FileWriter(prefix + "lisp/ww2d-" + act + ".lisp"));
				for (int i = 1; i <= n; ++i) {
					String filename = act + "/" + act + "-" + i + ".csv";
					System.out.println("Activity: " + filename);
					
					// Load global
					ww2d.load(prefix + "global/" + filename, DBType.Global);
					ww2d.doBooleanStream(DBType.Global, null, null);
					ww2d.doSDL(DBType.Global, new String[]{"distance"}, null);
					ww2d.doMoving(DBType.Global);
					ww2d.doRelative(DBType.Global);
					
					// Load agent
					ww2d.load(prefix + "agent/" + filename, DBType.Agent);
					ww2d.doInternalStates(DBType.Agent);
					
					// Load obj
					ww2d.load(prefix + "object/" + filename, DBType.Object);
					ww2d.doInternalStates(DBType.Object);
					
					// Convert to intervals
					List<Interval> intervals = new ArrayList<Interval>();
					intervals.addAll(ww2d.getIntervals(DBType.Global));
					intervals.addAll(ww2d.getIntervals(DBType.Agent));
					intervals.addAll(ww2d.getIntervals(DBType.Object));
					
					// Output episode
					out.write("(" + i + "\n");
					out.write(" (\n");
					for (Interval interval : intervals) { 
						out.write("(\"" + interval.name + "\" " + 
								interval.start + " " +
								interval.end + ")\n");
					}
					out.write(" )\n");
					out.write(")\n");
				}
				out.close();
			} catch (Exception e) { 
				e.printStackTrace();
			}
		}
	}
}
