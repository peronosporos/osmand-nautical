package net.osmand.search.core.spatial;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.osmand.binary.BinaryMapAddressReaderAdapter.AddressRegion;
import net.osmand.binary.BinaryMapIndexReader;
import net.osmand.binary.BinaryMapPoiReaderAdapter.PoiRegion;
import net.osmand.binary.NameIndexReader;
import net.osmand.map.OsmandRegions;
import net.osmand.util.SearchAlgorithms;

//////////////// SEARCH ALGORITHM //////////////////
// 1. Init files + read caches
// 2. Split tokens
// 3. Read tokens -> atoms (
// 4. Sort tokens to do combinations
// 5. Find combinations
// 6. Sort results, filter results
// 7. Expand poi categories if needed

////////////// FUTURE OPTIMIZATIONS ////////////////
// 1. PARTIAL SEARCH. Perform equals search and then with '.'
// 2. MAPS. Do search first with closest maps and then with others
// 3. ALL COMBINATIONS. Stop on one combination or find all
// 4. POI CATEGORIES. -? 
// 5. READ_ALL. Switch ALWAYS_READ_COMMON_WORDS_ATOMS=true (new results + school intersections)
// 6. OPTIMIZE POI READ. Read only 1 POI in block
////////////////////////////////////////////////////

public class SpatialTextSearch {

	private static final int LIMIT_PRINT = 1000;

	public static class SpatialTextSearchSettings {

		public boolean SEARCH_ADDR = true;
		public boolean SEARCH_POI = true;
		public boolean SEARCH_BUILDINGS = true;
		public boolean SEARCH_STREET_INTERSECTIONS = true;
		public boolean SEARCH_POI_INTERSECTIONS = true;
		// no intersection recorded but streets are nearby
		public boolean ALLOW_VIRTUAL_STREET_INTERSECTIONS = true;
		
		// once estimated intersections > limit we stop looking for these results (speed up)
		public int OPTIM_LIMIT_POI_OR_STREET_INTERSECTIONS = 10_000;
		public int OPTIM_LIMIT_POI_STREET_INTERSECTIONS = 500_000;
		
		public int[] OPTIM_LIMIT_RADIUS = new int[] {15_000, 50_000, 100_000}; // 10 km
//		public int[] OPTIM_LIMIT_RADIUS = new int[] {}; 
		public int OPTIM_LIMIT_INTERSECTIONS = 500_000; // 500K
		
		// max prefixes for each name reader
		public int AUTO_CLEAR_PREFIX_CACHE_LIMIT = 1000;

		// Deduplicate results in the end by checking osm id of the first object in combination
		public boolean DEDUPLICATE_RES = true;

		// READ OBJECTS before intersection to reduce number of duplicates from
		// different maps by osm id - needs to be tested performance mostly slows down
		// ! Potential issue READ_ADDR_OBJECTS could deduplicate streets and 
		//  building won't be found in case same street in cities
		public boolean DEV_READ_ADDR_OBJECTS = false;
		public boolean DEV_READ_POI_OBJECTS = false;

		// no need to find 3 street intersection or 3 POI intersection
		public int LIMIT_ATOMIC_OBJECTS = 2;

		// Very good optimization but breaks some scenarios
		// Performance improvement assuming for rare words we don't read common atoms
		// Problem search: New york plaza, New York 45 Avenue, School 40 on Specific Street.  
		public boolean ALWAYS_READ_COMMON_WORDS_ATOMS = true;
		public boolean ALWAYS_READ_FREQ_WORDS_ATOMS = true;

		// Limit evaluation intersection for unique objects
		public int LIMIT_ALL_GOALS_MAX_UNIQUE_OBJECTS = 1000;
		// if there are >= 10 results matching 5 words, 4 words match won't be considered
		public int LIMIT_GOAL_NEXT_LEVEL_MAX_UNIQUE_OBJECTS = 1; // could be 3
		// don't go level-2 if there are on level matching results
		public int LIMIT_GOAL_LEVEL_2 = 1;
		
		// Filter within same matched words but different number of objects [3 matched tokens - 1 single object]
		public int[] FILTER_MIN_WORDS_COUNT = new int[] {3, 10};
//		public int[] FILTER_MIN_WORDS_COUNT = new int[] {};
		
		// only do incomplete search with 2+ chars
		public int MIN_CHARACTERS_INCOMPLETE = 2;
		
		public int MIN_ELO_RATING = 1400; // see SearchResult.MIN_ELO_RATING
//		public int MAX_ELO_RATING = 4300; // not used now
		
	}

	public static class SpatialSearchFileCache {
		public int fileInd = -1; // changing each session - not concurrent !!!
		public int indexInd = -1; // changing each session - not concurrent !!!
		public final String file;
		public final long length;
		public final long edition;
		public final List<NameIndexReader> indexReaders = new ArrayList<NameIndexReader>();

		public SpatialSearchFileCache(BinaryMapIndexReader r) {
			file = r.getFile().getName();
			length = r.getFile().length();
			edition = r.getDateCreated();
			for (AddressRegion a : r.getAddressIndexes()) {
				indexReaders.add(new NameIndexReader(a));
			}
			for (PoiRegion a : r.getPoiIndexes()) {
				indexReaders.add(new NameIndexReader(a));
			}
		}

		public boolean test(BinaryMapIndexReader r) {
			return r.getFile().getName().equals(file) && r.getFile().length() == length
					&& r.getDateCreated() == edition;
		}
	}

	public static class SpatialSearchGlobalCache {

		public Map<String, SpatialSearchFileCache> filesCache = new HashMap<>();

	}

	public static class SpatialSearchResults {

		public String input;

		public List<SpatialSearchToken> tokens;

		public List<SpatialSearchResult> mainResults;

		public List<SpatialSearchResultsList> combinations;
		
		public SpatialSearchResult getFirstResult() {
			return mainResults == null || mainResults.size() == 0 ? null : 
				mainResults.get(0);
		}
	}

	SpatialSearchGlobalCache cache = new SpatialSearchGlobalCache(); // reusable between sessions

	private void sortTokens(List<SpatialSearchToken> tokens) {
		// sort from least atoms to do combinations as the most efficient
		Collections.sort(tokens, new Comparator<SpatialSearchToken>() {
			@Override
			public int compare(SpatialSearchToken o1, SpatialSearchToken o2) {
				int c1 = o1.atoms.size();
				int c2 = o2.atoms.size();
				if (c1 != c2) {
					return Integer.compare(c1, c2);
				}
				return o1.word.compareTo(o2.word);
			}

		});
		for (int i = 0; i < tokens.size(); i++) {
			tokens.get(i).sortedOrder = i;
		}
	}

	/**
	 * For [1, 2, 3, 4] Tokens evaluate with cache (- no cache, +in cache) longest chain 
	 * 1. Goal [1, 2, 3, 4]: -[1, 2], -[1, 2, 3], -[1, 2, 3, 4] 
	 * 2. Goal [1, 2, 3]: +[1, 2], +[1, 2, 3] 
	 * 3. Goal [1, 2, 4]: +[1, 2], -[1, 2, 4] 
	 * 4. Goal [1, 3, 4]: -[1, 3], -[1, 3, 4] 
	 * 5. Goal [2, 3, 4]: -[2, 3], -[1, 3, 4] 
	 * 6. Goal [1, 2]: +[1, 2] 
	 * 7. Goal [1, 3]: -[1, 3] ... 
	 * Once goal has enough results whole iteration stopped
	 * @param ctx
	 * @return
	 */
	List<SpatialSearchResultsList> findLongestCombinations(SpatialSearchContext ctx, List<SpatialSearchToken> tokens)
			throws IOException {
		List<SpatialSearchResultsList> fullResult = new ArrayList<SpatialSearchResultsList>();
		BitSet mainGoal = new BitSet();
		mainGoal.set(0, tokens.size());

		SpatialSearchResultsList root = new SpatialSearchResultsList();

		Map<BitSet, SpatialSearchResultsList> cache = new HashMap<BitSet, SpatialSearchResultsList>();

		int ind = 0;
		for (SpatialSearchToken t : tokens) {
			BitSet b = new BitSet();
			b.set(ind++);
			cache.put(b, new SpatialSearchResultsList(ctx, t, root));
			ctx.stats.tokenObjs += t.atoms.size();
		}

		LinkedList<BitSet> goals = new LinkedList<>();
		HashSet<BitSet> evaluated = new HashSet<>();
		goals.add(mainGoal);

		int uniqueObjects = 0;
		int depth = mainGoal.length();
		int maxDepth = 0;
		while (!goals.isEmpty()) {
			BitSet goal = goals.removeFirst();
			if (!evaluated.add(goal)) {
				continue;
			}
			// stop on level - 2
			if (maxDepth == 0) {
				if (uniqueObjects >= ctx.settings.LIMIT_GOAL_LEVEL_2) {
					maxDepth = depth;
				}
			} else if (goal.length() <= maxDepth - 2) {
				break;
			}
			// stop with condition on level - 1
			if (goal.length() < depth) {
				if (ctx.settings.LIMIT_GOAL_NEXT_LEVEL_MAX_UNIQUE_OBJECTS > 0
						&& uniqueObjects >= ctx.settings.LIMIT_GOAL_NEXT_LEVEL_MAX_UNIQUE_OBJECTS) {
					break;
				}
				depth = goal.length();
			}

			SpatialSearchResultsList goalRes = cache.get(goal);
//			System.out.println("EVALUATE GOAL " + goal + " " + (goalRes == null));
			if (goalRes == null) {
				BitSet eval = new BitSet();
				goalRes = root;
				for (int i = goal.nextSetBit(0); i >= 0; i = goal.nextSetBit(i + 1)) {
					SpatialSearchToken token = tokens.get(i);
					eval.set(i);
					if (!cache.containsKey(eval)) {
						goalRes = new SpatialSearchResultsList(ctx, token, goalRes);
						ctx.stats.maxCombinations = Math.max(ctx.stats.maxCombinations, goalRes.getCombinations()); 
//						System.out.println("  EVALUATE STEP " + eval + " " + goalRes);
						cache.put((BitSet) eval.clone(), goalRes);
					} else {
						goalRes = (SpatialSearchResultsList) cache.get(eval);
//						System.out.println("  <CACHE> STEP " + eval + " " + goalRes);
					}
				}
			}
			if (goalRes.getCombinations() > 0) {
				goalRes.loadObjectsAndCalcBuildings(ctx);
				List<SpatialSearchResult> res = goalRes.sortResults(ctx, ctx.settings.DEDUPLICATE_RES);
				uniqueObjects += res.size();
				fullResult.add(goalRes);
				if (ctx.settings.LIMIT_ALL_GOALS_MAX_UNIQUE_OBJECTS > 0
						&& uniqueObjects >= ctx.settings.LIMIT_ALL_GOALS_MAX_UNIQUE_OBJECTS) {
					break;
				}
			}
			BitSet nextGoal = (BitSet) goal.clone();
			for (int i = nextGoal.length(); (i = nextGoal.previousSetBit(i - 1)) >= 0;) {
				nextGoal.set(i, false);
				if (!nextGoal.isEmpty()) {
//					System.out.println("  <PUSH> GOAL " + nextGoal);
					goals.add((BitSet) nextGoal.clone());
				}
				nextGoal.set(i, true);
			}
		}
		return fullResult;
	}

	List<SpatialSearchResultsList> findObjCombinationsSimpleIteration(SpatialSearchContext ctx, List<SpatialSearchToken> tokens) {
		LinkedList<SpatialSearchResultsList> candidates = new LinkedList<>();
		candidates.add(new SpatialSearchResultsList());
		List<SpatialSearchResultsList> result = new ArrayList<>();
//		System.out.println("TOKENS " + tokens);

		while (!candidates.isEmpty()) {
			SpatialSearchResultsList parent = candidates.removeLast();
			if (parent.getCombinations() > 0) {
				result.add(parent);
			}
			for (int k = tokens.size() - 1; k >= 0; k--) {
//			for (SpatialSearchToken token : tokens) {
				SpatialSearchToken token = tokens.get(k);
				if (parent.getTokenCount() == 0 || token.sortedOrder < parent.getFirstToken().sortedOrder) {
					SpatialSearchResultsList next = new SpatialSearchResultsList(ctx, token, parent);
//					next.calculateIntersection(token, parent);
//					System.out.printf("ITERATION Token [%s] + {%s} = {%s}\n", token, parent, next);
					candidates.push(next);
				}
			}
		}
		return result;

	}

	public SpatialSearchResults searchAPI(String input, SpatialSearchContext ctx) throws IOException {
		SpatialSearchResults res = new SpatialSearchResults();
		ctx.initFiles(cache);
		res.input = input;
		// 1. prepare tokens
		res.tokens = splitWords(ctx, input);

		// 2. read atoms
		ctx.stats.stepAtoms -= System.nanoTime();
		ctx.readAtoms(res.tokens);
		ctx.stats.stepAtoms += System.nanoTime();

		// 3. sort tokens
		sortTokens(res.tokens);

		// 4. find combinations
		ctx.stats.stepCompute -= System.nanoTime();
//		res.combinations = findObjCombinationsSimpleIteration(res.tokens);
		res.combinations = findLongestCombinations(ctx, res.tokens);
		ctx.stats.stepCompute += System.nanoTime();
		// 5. sort combinations, load objects, objects and filter duplicate
		res.mainResults = new ArrayList<>();
		ctx.stats.stepSort -= System.nanoTime();
		if (res.combinations.size() > 0) {
			combineSortFilterResults(ctx, res);
		}
		ctx.stats.stepSort += System.nanoTime();
		return res;
	}

	private void combineSortFilterResults(SpatialSearchContext ctx, SpatialSearchResults res) {
		SpatialSearchResultsList main = res.combinations.get(0);
		for (SpatialSearchResultsList m : res.combinations) {
			List<SpatialSearchResult> lst = m.getFinalResult();
			if (lst == null) {
				lst = m.sortResults(ctx, ctx.settings.DEDUPLICATE_RES);
			}
			res.mainResults.addAll(lst);
		}
		res.mainResults = main.sortResults(ctx, res.mainResults, ctx.settings.DEDUPLICATE_RES);
		if (res.mainResults.size() > 0) {
			int[] limits = ctx.settings.FILTER_MIN_WORDS_COUNT.clone();
			int sz = res.mainResults.get(0).getObjectsSize(), ind = 0, lind = 0;
			int level = 0; 
			for (SpatialSearchResult r : res.mainResults) {
				if (sz != r.getObjectsSize()) {
					if (level == 0) {
						if (lind < limits.length && ind >= limits[lind]) {
							level++;
						} else if (lind < limits.length - 1) {
							lind++;
						}
					} else {
						level++;
					}
					sz = r.getObjectsSize();
				}
				r.level = level;
				ind++;
			}
		}
	}

	public List<SpatialSearchToken> splitWords(SpatialSearchContext ctx, String input) {
		List<String> owords = new ArrayList<String>();
		// split by hyphen as we supposed to index them separately
		List<String> words = SearchAlgorithms.splitAndNormalize(input, owords, false);
		List<SpatialSearchToken> tokens = new ArrayList<>();
		for (int order = 0; order < words.size(); order++) {
			String w = words.get(order);
			SpatialSearchToken token = new SpatialSearchToken(ctx.settings.MIN_CHARACTERS_INCOMPLETE, w, owords.get(order), order);
			tokens.add(token);
		}
		return tokens;
	}

	public SpatialSearchResults searchTest(String input, SpatialSearchContext ctx) throws IOException {
		SpatialSearchResults res = searchAPI(input, ctx);
		ctx.stats.finish();
		if (res.mainResults != null && res.mainResults.size() > 0) {
			System.out.println("--------");
			System.out.println("Main: " + res.combinations.get(0));
			int limit = LIMIT_PRINT;
			int all = res.mainResults.size();
			int level = 0;
			for (SpatialSearchResult r : res.mainResults) {
				if (r.level != level) {
					level++;
					System.out.println("### LEVEL " + level);
				}
				if (limit-- < 0) {
					System.out.println(".............");
					break;
				}
				System.out.printf("Result %d - %s\n", r.matchedTokens(), r);
			}
			System.out.printf("------ ALL %d results ------- \n ", all);
			System.out.println("---------------------------------------");
		}

		System.out.println("\nTokens: " + res.tokens);
		System.out.printf("All Combinations - %d: \n", res.combinations.size());
		for (SpatialSearchResultsList s : res.combinations) {
			if (s.getTokenCount() >= 2) {
				s.sortResults(ctx, true);
				System.out.println("  " + s.toString(false));
//				int limit = LIMIT_PRINT;
//				for (SpatialSearchResult r : s.getResult()) {
//					if (limit-- < 0) {
//						System.out.println(".............");
//						break;
//					}
//					System.out.println(r);
//				}
			}
		}

		System.out.println(ctx.stats);
		System.out.println();
		return res;
	}

	static void initFile(List<BinaryMapIndexReader> ls, File f) throws IOException, FileNotFoundException {
		if (f.exists() && (f.getName().endsWith(".obf") || f.getName().equals(OsmandRegions.REGIONS_OCBF))) {
			BinaryMapIndexReader bir = new BinaryMapIndexReader(new RandomAccessFile(f, "r"), f);
			ls.add(bir);
		}
	}


	public static void mainTest(String[] subArgsArray) throws FileNotFoundException, IOException {
		long t = System.nanoTime();
		String query = subArgsArray[0];
		List<BinaryMapIndexReader> ls = new ArrayList<BinaryMapIndexReader>();
		for (int i = 1; i < subArgsArray.length; i++) {
			File fl = new File(subArgsArray[i]);
			if (fl.isFile()) {
				if (i == 1) {
					initFile(ls, new File(fl.getParentFile(), OsmandRegions.REGIONS_OCBF));
				}
				if (!fl.getName().equals(OsmandRegions.REGIONS_OCBF)) {
					initFile(ls, fl);
				}
			} else {
				for (File f : fl.listFiles()) {
					initFile(ls, f);
				}
			}
		}
		System.out.println(String.format("Index files %.1f ms", (System.nanoTime() - t) / 1e6));
		SpatialTextSearch a = new SpatialTextSearch();
		SpatialSearchContext searchContext = new SpatialSearchContext(new SpatialTextSearchSettings(), ls, null);
		a.searchTest(query, searchContext);
	}

}