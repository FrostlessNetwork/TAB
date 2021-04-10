package me.neznamy.tab.shared.cpu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

import me.neznamy.tab.shared.ErrorManager;

/**
 * A class which measures CPU usage of all tasks inserted into it and shows usage
 */
public class CPUManager {

	//data reset interval in milliseconds
	private final int bufferSizeMillis = 10000;

	//nanoseconds worked in the current 10 seconds
	private Map<Object, Map<UsageType, AtomicLong>> featureUsageCurrent = new ConcurrentHashMap<Object, Map<UsageType, AtomicLong>>();
	private Map<String, AtomicLong> placeholderUsageCurrent = new ConcurrentHashMap<String, AtomicLong>();
	private Map<String, AtomicLong> bridgePlaceholderUsageCurrent = new ConcurrentHashMap<String, AtomicLong>();
	private Map<String, AtomicLong> methodUsageCurrent = new ConcurrentHashMap<String, AtomicLong>();
	
	//packets sent in the current 10 seconds
	private Map<Object, Integer> packetsCurrent = new ConcurrentHashMap<Object, Integer>();

	//nanoseconds worked in the previous 10 seconds
	private Map<Object, Map<UsageType, AtomicLong>> featureUsagePrevious = new HashMap<Object, Map<UsageType, AtomicLong>>();
	private Map<String, AtomicLong> placeholderUsagePrevious = new HashMap<String, AtomicLong>();
	private Map<String, AtomicLong> bridgePlaceholderUsagePrevious = new HashMap<String, AtomicLong>();
	private Map<String, AtomicLong> methodUsagePrevious = new HashMap<String, AtomicLong>();
	
	//packets sent in the previous 10 seconds
	private Map<Object, Integer> packetsPrevious = new ConcurrentHashMap<Object, Integer>();

	//thread pool
	private ThreadPoolExecutor exe = (ThreadPoolExecutor) Executors.newCachedThreadPool();

	//error manager
	private ErrorManager errorManager;

	/**
	 * Constructs new instance and starts repeating task that resets values every 10 seconds
	 * @param errorManager - error manager
	 */
	public CPUManager(ErrorManager errorManager) {
		this.errorManager = errorManager;
		exe.submit(new Runnable() {

			@Override
			public void run() {
				try {
					while (true) {
						Thread.sleep(bufferSizeMillis);

						featureUsagePrevious = featureUsageCurrent;
						placeholderUsagePrevious = placeholderUsageCurrent;
						bridgePlaceholderUsagePrevious = bridgePlaceholderUsageCurrent;
						methodUsagePrevious = methodUsageCurrent;
						packetsPrevious = packetsCurrent;

						featureUsageCurrent = new ConcurrentHashMap<Object, Map<UsageType, AtomicLong>>();
						placeholderUsageCurrent = new ConcurrentHashMap<String, AtomicLong>();
						bridgePlaceholderUsageCurrent = new ConcurrentHashMap<String, AtomicLong>();
						methodUsageCurrent = new ConcurrentHashMap<String, AtomicLong>();
						packetsCurrent = new ConcurrentHashMap<Object, Integer>();
					}
				} catch (InterruptedException pluginDisabled) {

				}
			}
		});
	}

	/**
	 * Returns amount of active and total threads
	 * @return active and total threads from this thread pool
	 */
	public String getThreadCount() {
		return exe.getActiveCount() + "/" + exe.getPoolSize();
	}

	/**
	 * Cancels all tasks, new instance is set to avoid errors when starting tasks on shutdown (such as packet readers)
	 */
	public void cancelAllTasks() {
		//preventing errors when tasks are inserted while shutting down
		ExecutorService old = exe;
		exe = (ThreadPoolExecutor) Executors.newCachedThreadPool();
		old.shutdownNow();
	}

	/**
	 * Starts a task in new thread and measures how long it took to process
	 * @param errorDescription - description to use if this task throws an error
	 * @param feature - feature to add cpu usage to
	 * @param type - usage type to add cpu usage to
	 * @param task - the task
	 */
	public void runMeasuredTask(String errorDescription, Object feature, UsageType type, Runnable task) {
		exe.submit(new Runnable() {

			public void run() {
				try {
					long time = System.nanoTime();
					task.run();
					addTime(feature, type, System.nanoTime()-time);
				} catch (Throwable t) {
					errorManager.printError("An error occurred when " + errorDescription, t);
				}
			}
		});
	}

	/**
	 * Runs task in a new thread
	 * @param errorDescription - description to use if this task throws an error
	 * @param task - the task
	 */
	public void runTask(String errorDescription, Runnable task) {
		exe.submit(new Runnable() {

			public void run() {
				try {
					task.run();
				} catch (Throwable t) {
					errorManager.printError("An error occurred when " + errorDescription, t);
				}
			}
		});
	}

	/**
	 * Starts a new task with defined repeat interval that measures cpu usage
	 * @param intervalMilliseconds - task interval
	 * @param errorDescription - description to use if this task throws an error
	 * @param feature - feature to add cpu usage to
	 * @param type - usage type to add cpu usage to
	 * @param task - the task
	 */
	public void startRepeatingMeasuredTask(int intervalMilliseconds, String errorDescription, Object feature, UsageType type, Runnable task) {
		if (intervalMilliseconds <= 0) return;
		exe.submit(new Runnable() {

			public void run() {
				long lastLoop = System.currentTimeMillis()-intervalMilliseconds;
				while (true) {
					try {
						long sleep = intervalMilliseconds - (System.currentTimeMillis()-lastLoop);
						if (sleep < 0) {
							sleep = 0;
						}
						Thread.sleep(sleep);
						lastLoop = System.currentTimeMillis();
						long time = System.nanoTime();
						task.run();
						addTime(feature, type, System.nanoTime()-time);
					} catch (InterruptedException pluginDisabled) {
						break;
					} catch (Throwable t) {
						errorManager.printError("An error occurred when " + errorDescription, t);
					}
				}
			}
		});
	}

	/**
	 * Runs task with a delay and measures how long it took to process
	 * @param delayMilliseconds - how long to run the task after
	 * @param errorDescription - description to use if this task throws an error
	 * @param feature - feature to add cpu usage to
	 * @param type - usage type to add cpu usage to
	 * @param task - the task
	 */
	public void runTaskLater(int delayMilliseconds, String errorDescription, Object feature, UsageType type, Runnable task) {
		exe.submit(new Runnable() {

			public void run() {
				try {
					Thread.sleep(delayMilliseconds);
					long time = System.nanoTime();
					task.run();
					addTime(feature, type, System.nanoTime()-time);
				} catch (InterruptedException pluginDisabled) {
				} catch (Throwable t) {
					errorManager.printError("An error occurred when " + errorDescription, t);
				}
			}
		});
	}

	/**
	 * Returns cpu usage map of placeholders from previous 10 seconds
	 * @return cpu usage map of placeholders
	 */
	public Map<String, Float> getPlaceholderUsage(){
		return getUsage(placeholderUsagePrevious);
	}

	/**
	 * Returns cpu usage map of placeholders on bukkit side from previous 10 seconds. This is only used
	 * when TAB is on bungeecord with PAPI placeholder support
	 * @return cpu usage map of placeholders
	 */
	public Map<String, Float> getBridgeUsage(){
		return getUsage(bridgePlaceholderUsagePrevious);
	}
	
	/**
	 * Returns cpu usage map of methods previous 10 seconds
	 * @return cpu usage map of methods
	 */
	public Map<String, Float> getMethodUsage(){
		return getUsage(methodUsagePrevious);
	}
	
	/**
	 * Returns map of sent packets per feature in previous 10 seconds
	 * @return map of sent packets per feature
	 */
	public Map<Object, Integer> getSentPackets(){
		return sortByValue(packetsPrevious);
	}

	/**
	 * Converts nano map to percent and sorts it from highest to lowest usage
	 * @param map - map to converted
	 * @return converted and sorted map
	 */
	private Map<String, Float> getUsage(Map<String, AtomicLong> map){
		Map<String, Long> nanoMap = new HashMap<String, Long>();
		String key;
		for (Entry<String, AtomicLong> nanos : map.entrySet()) {
			key = nanos.getKey();
			if (!nanoMap.containsKey(key)) nanoMap.put(key, 0L);
			nanoMap.put(key, nanoMap.get(key)+nanos.getValue().get());
		}
		Map<String, Float> percentMap = new HashMap<String, Float>();
		for (Entry<String, Long> entry : nanoMap.entrySet()) {
			percentMap.put(entry.getKey(), nanosToPercent(entry.getValue()));
		}
		return sortByValue(percentMap);
	}

	/**
	 * Returns map of CPU usage per feature per type in the previous 10 seconds
	 * @return map of CPU usage per feature per type
	 */
	public Map<Object, Map<UsageType, Float>> getFeatureUsage(){
		Map<Object, Map<UsageType, Long>> total = new HashMap<Object, Map<UsageType, Long>>();
		for (Entry<Object, Map<UsageType, AtomicLong>> nanos : featureUsagePrevious.entrySet()) {
			Object key = nanos.getKey();
			if (!total.containsKey(key)) {
				total.put(key, new HashMap<UsageType, Long>());
			}
			Map<UsageType, Long> usage = total.get(key);
			for (Entry<UsageType, AtomicLong> entry : nanos.getValue().entrySet()) {
				if (!usage.containsKey(entry.getKey())) {
					usage.put(entry.getKey(), 0L);
				}
				usage.put(entry.getKey(), usage.get(entry.getKey()) + entry.getValue().get());
			}
		}
		Map<Object, Map<UsageType, Float>> sorted = new LinkedHashMap<Object, Map<UsageType, Float>>();
		for (Object key : sortKeys(total)) {
			Map<UsageType, Long> local = sortByValue(total.get(key));
			Map<UsageType, Float> percent = new LinkedHashMap<UsageType, Float>();
			for (Entry<UsageType, Long> entry : local.entrySet()) {
				percent.put(entry.getKey(), nanosToPercent(entry.getValue()));
			}
			sorted.put(key, percent);
		}
		return sorted;
	}

	/**
	 * Converts nanoseconds to percentual usage
	 * @param nanos - nanoseconds worked
	 * @return usage in % (0-100)
	 */
	private float nanosToPercent(long nanos) {
		float percent = (float) nanos / bufferSizeMillis / 1000000; //relative usage (0-1)
		percent *= 100; //relative into %
		return percent;
	}

	/**
	 * Sorts map by value from higest to lowest
	 * @param <K> - map key
	 * @param <V> - map value
	 * @param map - map to sort
	 * @return sorted map
	 */
	private <K, V extends Comparable<V>> Map<K, V> sortByValue(Map<K, V> map) {
		Comparator<K> valueComparator =  new Comparator<K>() {
			public int compare(K k1, K k2) {
				int compare = map.get(k2).compareTo(map.get(k1));
				if (compare == 0) return 1;
				else return compare;
			}
		};
		Map<K, V> sortedByValues = new TreeMap<K, V>(valueComparator);
		sortedByValues.putAll(map);
		return sortedByValues;
	}

	/**
	 * Sorts keys by map nested values from highest to lowest and returns sorted list of keys
	 * @param <K> - map key
	 * @param <L> - nested map key
	 * @param map - map to sort
	 * @return list of keys sorted from highest usage to lowest
	 */
	private <K, L> List<K> sortKeys(Map<K, Map<L, Long>> map){
		Map<K, Long> simplified = new LinkedHashMap<K, Long>();
		for (Entry<K, Map<L, Long>> entry : map.entrySet()) {
			simplified.put(entry.getKey(), sumValues(entry.getValue()));
		}
		simplified = sortByValue(simplified);
		List<K> sortedKeys = new ArrayList<K>();
		for (K key : simplified.keySet()) {
			sortedKeys.add(key);
		}
		return sortedKeys;
	}

	/**
	 * Returns summary of all values in a map
	 * @param map - map to sum up values of
	 * @return summary of values
	 */
	private long sumValues(Map<?, Long> map) {
		long total = 0;
		for (Long value : map.values()) {
			total += value;
		}
		return total;
	}

	/**
	 * Adds cpu time to specified feature and usage type
	 * @param feature - feature to add time to
	 * @param type - usage to add time to of the feature
	 * @param nanoseconds - time to add
	 */
	public void addTime(Object feature, UsageType type, long nanoseconds) {
		Map<UsageType, AtomicLong> usage = featureUsageCurrent.get(feature);
		if (usage == null) {
			usage = new ConcurrentHashMap<UsageType, AtomicLong>();
			featureUsageCurrent.put(feature, usage);
		}
		try {
			if (!usage.containsKey(type)) {
				usage.put(type, new AtomicLong());
			}
		} catch (NullPointerException e) {
			//java.lang.NullPointerException: null
			//at java.base/java.util.concurrent.ConcurrentHashMap.get(ConcurrentHashMap.java:936)
			//at java.base/java.util.concurrent.ConcurrentHashMap.containsKey(ConcurrentHashMap.java:964)
			return;
		}
		AtomicLong current = usage.get(type);
		if (current == null) {
			usage.put(type, new AtomicLong(nanoseconds));
		} else {
			current.addAndGet(nanoseconds);
		}
	}

	/**
	 * Adds placeholder time to defined placeholder
	 * @param placeholder - placeholder to add time to
	 * @param nanoseconds - time to add
	 */
	public void addPlaceholderTime(String placeholder, long nanoseconds) {
		addTime(placeholderUsageCurrent, placeholder, nanoseconds);
	}

	/**
	 * Adds placeholder time to defined placeholder
	 * @param placeholder - placeholder to add time to
	 * @param nanoseconds - time to add
	 */
	public void addBridgePlaceholderTime(String placeholder, long nanoseconds) {
		addTime(bridgePlaceholderUsageCurrent, placeholder, nanoseconds);
	}
	
	/**
	 * Adds method time to defined method
	 * @param method - method to add time to
	 * @param nanoseconds - time to add
	 */
	public void addMethodTime(String method, long nanoseconds) {
		addTime(methodUsageCurrent, method, nanoseconds);
	}
	
	/**
	 * Adds used time to specified key into specified map
	 * @param map - map to add usage to
	 * @param key - usage key
	 * @param time - nanoseconds the task took
	 */
	private void addTime(Map<String, AtomicLong> map, String key, long time) {
		AtomicLong current = map.get(key);
		if (current == null) {
			map.put(key, new AtomicLong(time));
		} else {
			current.addAndGet(time);
		}
	}
	
	/**
	 * Increments packets sent by 1 of specified feature
	 * @param feature - feature to increment packet counter of
	 */
	public void packetSent(Object feature) {
		Integer current = packetsCurrent.get(feature);
		if (current == null) {
			packetsCurrent.put(feature, 1);
		} else {
			packetsCurrent.put(feature, current + 1);
		}
	}
}