package net.osmand.search.core.spatial;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.Set;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.hash.TLongHashSet;
import net.osmand.binary.BinaryMapPoiReaderAdapter;
import net.osmand.data.Amenity;
import net.osmand.data.Building;
import net.osmand.data.LatLon;
import net.osmand.data.MapObject;
import net.osmand.data.Street;
import net.osmand.search.core.HashQuadTree;
import net.osmand.search.core.spatial.SpatialSearchToken.NameIndexAtom;
import net.osmand.search.core.spatial.SpatialTextSearch.SpatialTextSearchSettings;
import net.osmand.util.SearchAlgorithms;


public class SpatialSearchResultsList implements Comparable<SpatialSearchResultsList> {
	final SpatialSearchToken[] tokens; // non modifiable!
	final int tCount;
	
	int MIN_ELO_RATING = Amenity.DEFAULT_ELO;
	
	public static long PARTIAL_ID_MATCH = -32; // special flag for building partial match

	// NameIndexAtom[][] -- should be double array to store list of combinations
	List<NameIndexAtom> linearResults = new ArrayList<>();
	TLongArrayList tileIds = new TLongArrayList();
	TIntArrayList tileZooms = new TIntArrayList();
	HashQuadTree<Integer> quadTree = new HashQuadTree<>(16);

	TIntObjectHashMap<Boolean> skipResults = new TIntObjectHashMap<>();
	Map<Integer, LatLon> preciseLocations = new HashMap<Integer, LatLon>();
	List<SpatialSearchResult> finalResult = null;
	
	public SpatialSearchResultsList() {
		this(null, null, null);
	}

	public SpatialSearchResultsList(SpatialSearchContext ctx, SpatialSearchToken token, SpatialSearchResultsList parent) {
		if (token == null) {
			tokens = new SpatialSearchToken[0];
		} else {
			tokens = new SpatialSearchToken[parent.tokens.length + 1];
			tokens[0] = token;
			for (int k = 0; k < parent.tokens.length; k++) {
				tokens[k + 1] = parent.tokens[k];
			}
		}
		tCount = tokens.length;
		if (parent != null) {
			MIN_ELO_RATING = ctx.settings.MIN_ELO_RATING;
			calculateMainIntersection(ctx, token, parent);
		}
	}
	
	private void loadObjects(SpatialSearchContext ctx, int type, TLongObjectHashMap<MapObject> cache) throws IOException {
		TLongObjectHashMap<Long> lstMap = new TLongObjectHashMap<>();
		
		Map<Integer, TLongHashSet> poiBboxes = new LinkedHashMap<Integer, TLongHashSet>();
		for (NameIndexAtom a : linearResults) {
			if (a.object != null) {
				cache.put(a.id, a.object);
				continue;
			}
			if (a.type == SpatialSearchToken.POI_TYPE) {
				int indInd = ctx.getFileInd(a.id);
				if(!poiBboxes.containsKey(indInd)) {
					poiBboxes.put(indInd, new TLongHashSet());
				}
				poiBboxes.get(indInd).add(HashQuadTree.encodeTileId31(BinaryMapPoiReaderAdapter.EVAL_TAG_GROUP_ZOOM,
						a.coords.x16 << 15, a.coords.y16 << 15));
			}
			if (a.type == type || (type == -2 && a.type != SpatialSearchToken.POI_TYPE
					&& a.type != SpatialSearchToken.STREET_TYPE)) {
				lstMap.put(a.id, a.parentid);
			} else if(type == -2 && a.type == SpatialSearchToken.STREET_TYPE) {
				lstMap.put(a.parentid, (long) 0);
			}
		}
		for (Map.Entry<Integer, TLongHashSet> poiBoxEntry : poiBboxes.entrySet()) {
			ctx.readPOIBboxes(poiBoxEntry.getKey(), poiBoxEntry.getValue());
		}
		TLongArrayList lst = new TLongArrayList(lstMap.keySet());
		lst.sort(); // sort is not correct for file ind last bits >>> 12 
		for(int i = 0; i < lst.size(); i++) {
			long id = lst.get(i);
			if (type == SpatialSearchToken.POI_TYPE) {
				cache.put(id, ctx.readPoiObject(id, cache));
			} else {
				cache.put(id, ctx.readAddrObject(id, lstMap.get(id), cache));
			}
		}
		for (NameIndexAtom a : linearResults) {
			MapObject mo = cache.get(a.id);
			if (mo != null) {
				a.object = mo;
			}
		}
	}
	
	public void loadObjects(SpatialSearchContext ctx) throws IOException {
		TLongObjectHashMap<MapObject> cache = new TLongObjectHashMap<MapObject>();
		loadObjects(ctx, SpatialSearchToken.POI_TYPE, cache);
		cache.clear();
		loadObjects(ctx, -2, cache);
		loadObjects(ctx, SpatialSearchToken.STREET_TYPE, cache);
	}
	
	public List<SpatialSearchToken> getMissingTokens(SpatialSearchContext ctx) {
		List<SpatialSearchToken> lst = new ArrayList<>(ctx.tokens);
		for (SpatialSearchToken s : tokens) {
			lst.remove(s);
		}
		return lst;
	}
	
	public void loadObjectsAndCalcBuildings(SpatialSearchContext ctx) throws IOException {
		ctx.stats.loadObjectsBld -= System.nanoTime();
		loadObjects(ctx);
		
		if (ctx.settings.SEARCH_BUILDINGS) {
			Map<String, Building> bldCheckCache = new HashMap<>();
			for (int indx = 0; indx < getCombinations(); indx++) {
				calcBuilding(indx, bldCheckCache);
			}
		}
		if (ctx.settings.SEARCH_STREET_INTERSECTIONS ) {
			for (int indx = 0; indx < getCombinations(); indx++) {
				calcStreetIntersections(ctx, indx);
			}
		}
		
		ctx.stats.loadObjectsBld += System.nanoTime();
	}
	
	private void calcStreetIntersections(SpatialSearchContext ctx, int indx) {
		NameIndexAtom first = null;
		NameIndexAtom second = null;
		for (int i = 0; i < tCount; i++) {
			NameIndexAtom atom = linearResults.get(indx * tCount + i);
			if (atom.object instanceof Street) {
				if (first == null || first.object.getId().equals(atom.object.getId())) {
					first = atom;
				} else {
					second = atom;
					break;
				}
			}
		}
		if (first != null && second != null) {
			LatLon insLoc = null; 
			if (insLoc == null) {
				for (Street ins : ((Street) first.object).getIntersectedStreets()) {
					if (ins.getName().equals(second.object.getName())) {
						insLoc = ins.getLocation();
//						System.out.printf("INTERSECTION x1 %.4f, %.4f %s (%s) x %s\n", insLoc.getLatitude(),
//								insLoc.getLongitude(), second.toString(), ins.getName(), first.toString());
						break;
					}
				}
			}
			if (insLoc == null) {
				for (Street ins : ((Street) second.object).getIntersectedStreets()) {
					if (ins.getName().equals(first.object.getName())) {
						insLoc = ins.getLocation();
//						System.out.printf("INTERSECTION x2 %.4f, %.4f %s (%s) x %s\n", ins.getLocation().getLatitude(),
//								ins.getLocation().getLongitude(), first.toString(), ins.getName(), second.toString());
						break;
					}
				}
			}
			if (insLoc == null && ctx.settings.ALLOW_VIRTUAL_STREET_INTERSECTIONS) {
				LatLon l1 = first.object.getLocation();
				LatLon l2 = second.object.getLocation();
				insLoc = new LatLon(l1.getLatitude() / 2 + l2.getLatitude() / 2, l1.getLongitude() / 2 + l2.getLongitude() / 2);
// 				System.out.printf("INTERSECTION NO %.4f %.4f %s x %s\n", insLoc.getLatitude(),
//						insLoc.getLongitude(), first.toString(), second.toString());
			}
			if (insLoc != null) {
				preciseLocations.put(indx, insLoc);
			} else {
				skipResults.put(indx, true);
			}
		}
	}
	
	private void calcBuilding(int indx, Map<String, Building> bldCheckCache) {
		Map<NameIndexAtom, String> bldCheckMap = null;
		
		for (int i = 0; i < tCount; i++) {
			NameIndexAtom bld = linearResults.get(indx * tCount + i);
			if (bld.buildingInd >= 0) {
				
				int strTokenInd = getTokenByOriginalOrder(bld.buildingInd);
				if (strTokenInd < 0) {
					skipResults.put(indx, true);
					break;
				}
				NameIndexAtom str = linearResults.get(indx * tCount + strTokenInd);
				if (str.id != bld.id) {
					if(bld.id == 1284571918336l) {
						System.out.println("-------");
					}
					continue;
				}
				if (bldCheckMap == null) {
					bldCheckMap = new HashMap<>();
				}
				String searchKey = bldCheckMap.get(str);
				if (searchKey == null) {
					searchKey = tokens[i].word;
				} else {
					searchKey += " " + tokens[i].word;
				}
				bldCheckMap.put(str, searchKey);
			}
		}
		// check many buildings on same street possibly unit or ref
		if (bldCheckMap != null) {
			Iterator<Entry<NameIndexAtom, String>> it = bldCheckMap.entrySet().iterator();
			// usually map is just single street
			while (it.hasNext()) {
				Entry<NameIndexAtom, String> e = it.next();
				NameIndexAtom str = e.getKey();
				String bldName = e.getValue();
				String cacheKey = str.id + " " + bldName;
				Building bldObj = null;
				if (bldCheckCache.containsKey(cacheKey)) {
					bldObj = bldCheckCache.get(cacheKey);
				} else {
					bldObj = checkBuilding((Street) str.object, bldName);
					if (bldObj == null) {
//						System.out.printf("No building '%s': %s\n", bldName, str.object);
					} else {
//						System.out.printf("Building found '%s' -'%s': %s\n", bldObj, bldName, str.object);
					}
					bldCheckCache.put(cacheKey, bldObj);
				}
				if (bldObj == null) {
					skipResults.put(indx, true);
					break;
				} else {
					// assign buildings
					for (int i = 0; i < tCount; i++) {
						NameIndexAtom bld = linearResults.get(indx * tCount + i);
						if (bld.buildingInd >= 0 && str.id == bld.id) {
							bld.object = bldObj;
							// bld.name = bldObj.getName();
						}
					}
					if (bldObj.isInterpolation()) {
						preciseLocations.put(indx, bldObj.getLocation(bldObj.interpolation(bldName)));
					}
				}
			}
		}
	}
	
	
	
	private Building checkBuilding(Street street, String bld) {
		Building interpolation = null;
		Building partial2 = null;
		Building partial1 = null;
		Set<String> original = SearchAlgorithms.getBuildingCompareSet(bld);
		for (Building b : street.getBuildings()) {
			if (b.isInterpolation()) {
				// interpolation only over 1 set
				if (original.size() == 1 && b.belongsToInterpolation(original.iterator().next())) {
					interpolation = b;
				}
			} else {
				Set<String> cmp = SearchAlgorithms.getBuildingCompareSet(b.getName());
//				System.out.println(street + " " + original + " ?= " + cmp);
				if (cmp.equals(original)) {
					// exact
					return b;
				}
				if (cmp.size() == original.size() + 1) {
					// case data only 18-B present, 18 searched
					if (cmp.containsAll(original)) {
						partial1 = b;
					}
				} else if (cmp.size() + 1 == original.size()) {
					// case data only 18 present, 18-B searched 
					if (original.containsAll(cmp)) {
						partial2 = b;
					}
				}
			}
		}
		if (partial1 != null) {
			return partial1;
		}
		if (interpolation != null) {
			return interpolation;
		}
		if (partial2 != null) {
			partial2.setId(PARTIAL_ID_MATCH);
			return partial2;
		}
		return null;
	}

	private int getTokenByOriginalOrder(int originalOrder) {
		for(int ind = 0; ind < tokens.length; ind++) {
			if (tokens[ind].originalOrder == originalOrder) {
				return ind;
			}
		}
		return -1;
	}

	public SpatialSearchToken getFirstToken() {
		return tokens.length == 0 ? null : tokens[0];
	}
	
	public int nearbyResult(int ind) {
		int min = 100;
		for (int i = 0; i < tCount; i++) {
			NameIndexAtom ni = linearResults.get(ind * tCount + i);
			min = Math.min(ni.nearbyRadius, min);
		}
		return min;
	}

	public int getCombinations() {
		return tileIds.size();
	}

	public int getTokenCount() {
		return tCount;
	}
	
	public List<SpatialSearchResult> getFinalResult() {
		return finalResult;
	}

	public List<SpatialSearchResult> sortResults(SpatialSearchContext ctx, boolean deduplicate) {
		finalResult = new ArrayList<>(tileIds.size());
		for (int i = 0; i < tileIds.size(); i++) {
			if (!skipResults.containsKey(i)) {
				finalResult.add(new SpatialSearchResult(this, i, preciseLocations.get(i)));
			}
		}		
		finalResult = sortResults(ctx, finalResult, deduplicate);
		return finalResult;
	}

	public List<SpatialSearchResult> sortResults(SpatialSearchContext ctx, List<SpatialSearchResult> finalResult, boolean deduplicate) {
		Collections.sort(finalResult, (o1, o2) -> SpatialSearchResult.compare(o1, o2, ctx.location));
		if (deduplicate) {
			List<SpatialSearchResult> res = new ArrayList<SpatialSearchResult>();
			TLongHashSet duplicateIds = new TLongHashSet();
			for (SpatialSearchResult s : finalResult) {
				long filterId = s.getIdDeduplication();
				if (filterId > 0 && !duplicateIds.add(filterId)) {
					continue;
				}
				res.add(s);
			}
			finalResult = res;
		}
		return finalResult;
	}
	
	
	
	private void iterateIntersection(SpatialSearchResultsList parent, SpatialSearchToken token, 
			BiConsumer<Integer, NameIndexAtom> consumer) {
		// 1. iterate parent objects and find all objects from <parent>
		//    that are fully inside object <token> or have same the same tile
		for (int i = 0; i < parent.tileIds.size(); i++) {
			long tileId = parent.tileIds.get(i);
			int zoom = parent.tileZooms.get(i);
			final int indx = i;
			token.quadTree.forEachMatch(zoom, tileId, atoms -> {
				for (NameIndexAtom a : atoms) {
					consumer.accept(indx, a);
				}
			});
		}
		// 2. reverse search quad tree from <token> and find objects
		//    that are fully inside any object <parent> and not the same the same tile!
		for (final NameIndexAtom a : token.atoms) {
			parent.quadTree.forEachMatchHigherZoom(a.coords.bboxTileZoom, a.coords.bboxTileId, indxs -> {
				for (int indx : indxs) {
					consumer.accept(indx, a);
				}
			});
		}
	}
 	
	private void calculateMainIntersection(SpatialSearchContext ctx, SpatialSearchToken token, SpatialSearchResultsList parent) {
		if (parent.getTokenCount() == 0) {
			for (NameIndexAtom atom : token.atoms) {
				addResult(null, 0, atom);
			}
		} else if (parent.getCombinations() > 0) {
			long[] countApproximate = new long[2];
			long nt = System.nanoTime();
			iterateIntersection(parent, token, (indx, a) -> { countApproximate[1]++; });
			int limitByRadius = ctx.limitLocationBboxes.length;
			countApproximate[0] = countApproximate[1];
			while (limitByRadius > 0 && countApproximate[0] > ctx.settings.OPTIM_LIMIT_INTERSECTIONS) {
				countApproximate[0] = 0;
				final int lmtNearby = limitByRadius;
				iterateIntersection(parent, token, (indx, atom) -> {
					if (atom.nearbyRadius > lmtNearby || parent.nearbyResult(indx) > lmtNearby) {
						return;
					}
					countApproximate[0]++;
				});
				limitByRadius--;
			}
			System.out.printf("Approximate (%.0f ms) /\\: %d (%,d) -> %d (%,d): '%s' (%,d) + %s (%,d)\n", 
					(System.nanoTime() - nt) / 1e6,
					ctx.limitLocationBboxes.length, countApproximate[1], limitByRadius,countApproximate[0],  
					token.originalWord, token.atoms.size(),
					parent.wordTokens(), parent.getCombinations());

			final int lmtNearby = limitByRadius;
			iterateIntersection(parent, token, (indx, atom) -> {
				if (atom.nearbyRadius > lmtNearby || parent.nearbyResult(indx) > lmtNearby) {
					return;
				}
				boolean acceptIntersection = acceptIntersection(ctx, countApproximate[0], parent, indx, token, atom);
				if (!acceptIntersection) {
					return;
				}
				addResult(parent, indx, atom);
			});
			System.out.printf("Actual (%.0f ms) /\\: %,d res \n", (System.nanoTime() - nt) / 1e6, getCombinations());
		}		
	}


	public int sumTokenAtomSize() {
		int sum = 0;
		for (SpatialSearchToken s : tokens) {
			sum += s.atoms.size();
		}
		return sum;
	}

	boolean addResult(SpatialSearchResultsList parent, int pindx, NameIndexAtom a) {
		finalResult = null;
		int pzoom = parent == null ? 0 : parent.tileZooms.get(pindx);
		int zoom = Math.max(pzoom, a.coords.bboxTileZoom);
		long tileId = pzoom > a.coords.bboxTileZoom ? parent.tileIds.get(pindx) : a.coords.bboxTileId;
		int insIndx = this.tileIds.size();
		this.linearResults.add(a);
		for (int i = 0; parent != null && i < parent.tCount; i++) {
			this.linearResults.add(parent.linearResults.get(pindx * parent.tCount + i));
		}

		this.tileIds.add(tileId);
		this.tileZooms.add(zoom);
		quadTree.put(zoom, tileId, insIndx);
		return true;
	}

	private boolean acceptIntersection(SpatialSearchContext ctx, long approximateRes, 
			SpatialSearchResultsList parent, int pindx, SpatialSearchToken token, NameIndexAtom a) {
		SpatialTextSearchSettings settings = ctx.settings;
		// 1. Precise intersection
		// no cache for parent now needed
		boolean intersect = true;
		for (int i = 0; parent != null && i < parent.tCount; i++) {
			NameIndexAtom pa = parent.linearResults.get(pindx * parent.tCount + i);
			if (!pa.coords.intersects(a.coords)) {
				intersect = false;
				break;
			}
		}
		if (!intersect) {
			return false;
		}
		// 2. Don't allow intersect potential building with other object
		boolean noStreetIntersect = !settings.SEARCH_STREET_INTERSECTIONS  
				|| (approximateRes > settings.OPTIM_LIMIT_POI_OR_STREET_INTERSECTIONS);
		boolean noPoiIntersect = !settings.SEARCH_POI_INTERSECTIONS 
				|| (approximateRes > settings.OPTIM_LIMIT_POI_OR_STREET_INTERSECTIONS);
		boolean noStreetPoiIntersect = (approximateRes > settings.OPTIM_LIMIT_POI_STREET_INTERSECTIONS);
		for (int i = 0; parent != null && i < parent.tCount; i++) {
			NameIndexAtom pa = parent.linearResults.get(pindx * parent.tCount + i);
			if (pa.id == a.id) {
				continue;
			}
			// pa and a using same tokens for street & house but different streets / poi - same as below
			if (parent.tokens[i].originalOrder == a.buildingInd) {
				return false;
			} else if (pa.buildingInd == token.originalOrder) {
				return false;
			}
			// don't intersect building with other street
			if ((pa.buildingInd >= 0) && a.streetBuilding()) {
				return false;
			} else if ((a.buildingInd >= 0) && pa.streetBuilding()) {
				return false;
			}
			if (pa.streetBuilding() && a.streetBuilding() && noStreetIntersect) {
				return false;
			}
			if (noStreetPoiIntersect && a.type == SpatialSearchToken.POI_TYPE && pa.streetBuilding() ) {
				// could be different for poi with bbox 
				return false;
			} else if (noStreetPoiIntersect && pa.type == SpatialSearchToken.POI_TYPE && a.streetBuilding() ) {
				return false;
			}
			if (noPoiIntersect && a.type == SpatialSearchToken.POI_TYPE && pa.type == a.type) {
				// could be different for poi with bbox 
				return false;
			}
		}
		
		// 3. Duplicate words		
		for (int i = 0; parent != null && i < parent.tCount; i++) {
			NameIndexAtom pa = parent.linearResults.get(pindx * parent.tCount + i);
			if (pa.id == a.id && tokens[0].word.equals(tokens[i + 1].word)) {
				// NameIndexAtom supports "<word> <...> <word>" but it's not present in DATA now
				int indexOf = a.name.indexOf(tokens[0].word, pindx);
				if (indexOf != -1 && a.name.indexOf(tokens[0].word, indexOf + 1) >= 0) {
					// duplicate name in object
				} else {
					return false;
				}
			}
			if (!pa.coords.intersects(a.coords)) {
				intersect = false;
				break;
			}
		}
		
		// 4. ignore multiple atomic objects intersections POI / Streets > 2!
		//    Building + Street (counts as 2 objects) - no (Building + Street 1 + Street 2)
		if (a.atomicObject()) {
			// check limit atomic objects to add
			List<Long> objects = new ArrayList<Long>(settings.LIMIT_ATOMIC_OBJECTS);
			objects.add(a.id);
			for (int i = 0; parent != null && i < parent.tCount; i++) {
				NameIndexAtom pa = parent.linearResults.get(pindx * parent.tCount + i);
				if (pa.atomicObject()) {
					long id = pa.id;
					if (pa.buildingInd >= 0) {
//						id += Integer.MAX_VALUE;
					}
					if (!objects.contains(id)) {
						objects.add(id);
					}
					//    Don't intersect <City Street> ('<Salt Lake City>') with Street ('Pennsylvania street')
					if ((a.isCityStreetName() && pa.id != a.id) || (pa.isCityStreetName() && a.id != pa.id)) {
						return false;
					}
				}
				if (objects.size() > settings.LIMIT_ATOMIC_OBJECTS) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public int compareTo(SpatialSearchResultsList o) {
		int s1 = tCount;
		int s2 = o.tCount;
		if (s1 != s2) {
			return -Integer.compare(s1, s2);
		}
		s1 = sumTokenAtomSize();
		s2 = o.sumTokenAtomSize();
		return Integer.compare(s1, s2);
	}

	public String toString(boolean extended) {
		List<String> words = wordTokens();
		return String.format("Results %d tokens %,d%s - %s %s", tCount, getCombinations(),
				finalResult == null ? "" : String.format(" (%,d unique)", finalResult.size()), 
				words, !extended ? "" : (" results: " + linearResults));
	}

	public List<String> wordTokens() {
		List<String> words = new ArrayList<>();
		for (SpatialSearchToken t : tokens) {
			words.add(t.originalWord);
		}
		return words;
	}
	
	

	@Override
	public String toString() {
		return toString(false);
	}

	

	
}