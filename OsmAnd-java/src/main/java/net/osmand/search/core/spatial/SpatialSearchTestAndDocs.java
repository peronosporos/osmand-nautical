package net.osmand.search.core.spatial;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.osmand.binary.BinaryMapIndexReader;
import net.osmand.data.City;
import net.osmand.data.LatLon;
import net.osmand.data.MapObject;
import net.osmand.map.OsmandRegions;
import net.osmand.search.core.spatial.SpatialTextSearch.SpatialSearchResults;
import net.osmand.search.core.spatial.SpatialTextSearch.SpatialTextSearchSettings;
import net.osmand.util.SearchAlgorithms;


//////////// UNIT TESTS ///////
// - Unit test: duplicate words in query Pennsylvania Street in Pennsylvania, Find More
// - Unit test: (<common_word> <almost_number>) -('№25', '25', '#25' - no search) -- OK ('школа', 'школа №25', 'школа 25', 'школа #25')
// - Unit test: Бульварно-Кудрявська, NC-42, 2-га Нова (2 Нова), M2...

//////////// SLOWEST /////////
// - QUERY: 'New York 4 av' - 7.5s (2M), 'New York st' - 2s (700k),
//   OTHER: 'New York s. ' 0.5s (100k), 'Sokak 2' - 0.5s (500K), 'Lima Calle 2' - 0.5s (25K)
// ANALYSIS:   0. york - 1897 atoms, 1. new - 2159 atoms, 2. 4 - 5776 atoms, 3. av - 8067 atoms
// ALL Stats 9525.7 ms - 54.6 ms 17,899 atoms (read 0.0, match 30.7), 9168.0 ms compute 2,357,716 (loadBld 924.3, read 335.8)
// NO STREET 1737.2 ms - 264.1 ms 17,899 atoms (read 162.5, match 84.2), 1445.2 ms compute 95,842 (loadBld 177.3, read 95.8)
// NO INTERS 1108.2 ms - 257.5 ms 17,899 atoms (read 148.5, match 93.6), 824.5 ms compute 22,690 (loadBld 92.4, read 74.0)

//////////// TESTING //////////
// REVIEW UI FILTER_MIN_WORDS_COUNT - 'New york plaza' ('the'), Issues Nova poshta Kharkiv 
// Document TOKENIZER (split) - COLLATOR: '#3', 'str.', 'U.S. Bank' ,'2-st' vs '2'  (Unit tests)
// bis matching
// !!! Building interpolation, Street intersection match
// TESTING 2га нова
// TESTING 'LangeStraße' (Data 'Lange Straße')

////////// IN PROGRESS//////////
// FIXME 'Daimler strasse' (Data 'daimlerstraße')
// FIXME Slow 'New York 4 av' - 7.5s (1M), 'New York st' - 2s (700k) - OPTIMAL OPTIM_LIMIT_INTERSECTIONS
// TODO Sokak 2 order
// TODO Filter results boundaries, <Salt Lake City>

// FIXME POI Categories + top poi categories
// FIXME Combine by osmid (poi type internet) & wikidata id ? osm id for routes (?)
//    Combine regions.ocbf (boundary)

// TO DO
// TODO POI Categories translations / synonyms
// TODO Progress / cancel
// TODO Web add regions.ocbf and 2nd search to search (Ksenia) - test "Arizona"
// TODO Inspector stats index_words_dashboard.html
// TODO Not forget to include regions.ocbf on client

// TEST IDEAS
// TODO test: merge boundaries bbox - extend incomplete boundary same id...
// TODO Ignore same embedded boundary city / county - deduplicate on the fly
// TODO ? review settings: read objects in between - Results 5 tokens 1,949 (139 unique) 
// TODO ? Store wikidata id for boundaries (regions.ocbf) & display them - place=county, place=state ? 
// TODO ? in the end recheck bbox boundary (full?) after load coordinates 31 (not 15) - chernihiv sport life
// TODO Test memory on Android device for slowest query

// EXTRA FEATURES
// TODO Postcode needs to load street and check buildings! Store postcode as bbox not as City! - '1186RZ 324' (NL, UK) 
// TODO Search in large parks, neighborhood same as in boundaries (index bbox POI), residential way/56238205
// TODO Search near key objects (subway station artificial bbox)
// TODO New Geocoding for cases ("NC 42" == "NC-42") 
// TODO Add flats: https://www.openstreetmap.org/node/5843642738
// TODO Sugggestion-correction
// TODO English postcodes

public class SpatialSearchTestAndDocs {

	/**
	 * Collator examples:
	 * Equals / starts from space
	 * TRUE - 's' in 'U.S. Information' (. is a space in collator)
	 * FALSE - 'us'  'U.S. Information' (no)
	 * TRUE - 'M-2' == 'M 2' (collator feature)
	 * 
	 * Tokenize:
	 * 'NA-75' - ['NA-75'] (- in between numbers),'NA 75' ['NA', '75']
	 * 'U.S. State' - ['U.S.', 'State'] (dot part of word)
	 * Friedrich-Wilhelm-Weber-Straße -  [friedrich, wilhelm, weber, straße]
	 * 
	 * Matcher
	 * 1. Exact matching always work
	 * 2. 'NA-75' matches 'NA 75' and 'NA 75' matches 'NA-75' 
	 * 
	 * Tokenizer {@link SearchAlgorithms#splitAndNormalize(String, boolean)}
	 * 
	 * Word: Characters or digits (emoji undefined status)
	 *  
	 * **Special symbols**
	 * '.' - part of the word: 'st.', '2039.' (needs to be stored inside)
	 * ''' - part of the word: 'Mcdonald's' (ignored in collator - alignChars)
	 * '-' - split not numbers, for numbers part of the word 
	 * Example: split used for user input '63/28' should keep as 1 word for building
	 * Special needs to be stored but ignored in collator
	 * 
	 * Other symbols are ignored:
	 * '#', '№', '/' ...
	 * 
	 * 1. Unnecessary split of 'NC-42', '2-B' '63/28' (housenumber reverted) causes 
	 * unnecessary complication and computation.
	 * 
	 * 2. No split causes '63/28' causes unnecessary indexing of refs like '123/1x/23y'
	 *    and missing search for '12/NameOfThePlace'
	 * 
	 * It's important to not split what has different meaning on reordering!
	 * However algorithm should support match and search for split words:
	 * DATA: '2-nd street '. SEARCH: 'Street 2', 'Street #2', Street 2-nd'
	 * DATA: 'NC 42', 'NC-42'. SEARCH: 'NC-42', 'NC 42
	 * 
	 * Index stores all single tokens except Partial Numbers and some Common.
	 * So index could have: 'NC-42', 'MC20', '2-nd' (2 letters)
	 * But not stores: '63/28', '2B', 'B2', 
	 * --------------------------------
	 * Spical cases:
	 * 1. '2nd street' is indexed as '2nd' and not 'street.
	 * 	  Limitation: user *must* input 2nd as part of search.
	 *    For input '2' or '#2' (pure number): indexes read all matching prefixes like '2nd'...
	 * 2. Data 'NC 42', ok indexed under 'NC'. (2 M)
	 *    Query: 'NC-42' will find 'NC' prefix and will match collator "NC 42" atom.
	 *    It always works with 2nd word number, if it's not number it will be 2 words.
	 * 3. Data '2 M'. Indexed only by letter. So it's not searchable as '2M'
	 * Potential issue:
	 *    '35bis' == '35 bis' - however it's only for house numbers where different tokenizer is applied!
	 * 4. Issue with space
	 *    Friedrich-Wilhelm-Weber-Straße split same as 'Friedrich Wilhelm Weber Straße' - 4 tokens
	 *    That's an issue for 'Weberstrasse' -> Weber strasse, Hemauerstraße -> Hemauer straße.
	 *    Possible solution is to prepare 2 variation during indexing 
	 */
	public static void main(String[] args) throws IOException, InterruptedException {
//		SpatialTextSearchSettings.DEDUPLICATE_RES = true;
//		SpatialTextSearchSettings.SEARCH_BUILDINGS = false;
		File folder = new File(System.getProperty("maps.dir"));
		LatLon location = null;
		String pattern = "Germany_b";
		String pattern2 = ".....";
		String query = "Berlin hauptstrasse"; // slow
//		query = "Kelterstraße Kernen im Remstal";
//		query = "Germany Kelter. Kernen im Remstal";
		query = "3 Hofäckerstraße Kernen im Remstal";
		
		// Weberstraße (33164748) 49.2041 10.7035,  Von-Weber-Straße (4648613942) 49.5609 10.8685
		query = "Weber Straße"; // TODO +4648613942, -33164748
//		query = "WeberStraße";  // +33164748, +4648613942
//		query = "Von Weberstraße"; // +4648613942
//		query = "53 Langestraße Waiblingen"; // OK !
		query = "69 Daimler Straße Stuttgart"; // TODO Daimlerstraße 107868593 48.8015 9.2224
		

		// Building time vs no building
//		Search Stats 778.5 ms - read 754.6 ms atoms (tokens 442.4 ms, obj 1.8 ms), match 281.5 ms, comp 26.4 ms
//		Search Stats 925.5 ms - read 799.8 ms atoms (tokens 442.5 ms, obj 16.3 ms), match 280.5 ms, comp 149.5 ms
		
//		pattern = "Us_";
//		pattern = "Map";
//		query = "Salt Lake City Pennsylvania Place 123 UT USA";
//		query = "Salt Lake City Elephant";
//		query = "Salt Lake City Lake";
//		query = "Salt Lake City Pennsylvania Street";
//		query = "West Valley City";
//		query = "USA Salt Lake City Pennsylvania Street 41";
//		query = "Pennsylvania Avenue Pennsylvania USA"; // 31372516
//		query = "Pennsylvania Avenue Philadelphia Pennsylvania USA";
//		query = "Pennsylvania Avenue Philadelphia PA USA"; 
//		query = "Pennsylvania Avenue Philadelphia Philadelphia County Pennsylvania USA";
//		query = "Pennsylvania Avenue White Oak Allegheny County Pennsylvania USA"; // 11947214
		
		// Street ref "pa 75" (not stored), house "pa-75" (data)
//		query = "PA 75 27193"; // Data 'PA-75', 27193  4472676432
//		query = "PA 75"; // Yes - ('PA 75', 'PA-75'), no - 'PA75' 
		// data "PA 75" - see "M-2, 2 M" example

//		pattern = "Liechtenstein_europe.obf";
//		query = "Vaduz Lettstrasse";
//		query = "Vaduz ";
//		query = "Jugendheim Malbun";

//		pattern = "Netherlands_";
//		query = "1186RZ Logger 324D Amstelveen";
//		query = "Farm";
		
//		pattern = "Turkey_";
//		query = "Sokak 23018. Balikesir"; // OK
//		query = "2301. Sokak"; // Test 23018., 23018 - Fixed NameIndexCreator - parsePureIntegerSuffix
		// ALL - Search Stats 1569.2 ms - 554.0 ms 59,656 atoms (read 318.8, match 134.1), 985.8 ms compute 693,139 (loadBld 396.2, read 149.5)
        // NO INTER - Search Stats 871.5 ms - 546.4 ms 59,656 atoms (read 313.7, match 135.6), 299.9 ms compute 4,735 (loadBld 54.1, read 37.2)
//		 query = "Sokak 2"; 
//		query = "2/1 21038 Sokak"; // 1380369156
		
		
//		pattern = "regions.ocbf" ;
		
		pattern = "Ukraine_";
//		pattern = "Map";
//		query = "Kyiv Глушкова 1"; // vs 'Kyiv 1'
//		query = "нова пошта Бульварно Кудрявська";
//		query = "Бульварно-кудрявс.";
//		query = "Ukraine kyiv saks.";
//		query = "пузата хата mcdonal.";
//		query = "Нова пошта 3 харків";
//		query = "Нова пошта харків";
//		query = "2 га Нова вулиця"; // unit test '2га' +, '2-га', '2', '2 га' (partial) unit test (260537333, 104438019)
		query = "2га Нова вулиця"; 
//		query = "саксаг. 63 28"; // 129-Б, 129б 63/28, 63, 63-28  +'саксаг. 63 28'
//		query = "саксаг. 63/28, 2";
//		query = "саксаг. 63/28 подъезд 2";
//		query = "саксаг. Володимирська"; // intersection
//		query = "саксаг. тарас."; // intersection
//		query = "54-та Садова вулиця 8"; // interpolation
//		query = "Яр. вал 29-г";
//		query = "25 Школа володимирська вулиця"; // ALWAYS_READ_COMMON_WORDS_ATOMS = true or show category (centre ?) ! 
//		query = "андріівський узвіз Школа "; // ALWAYS_READ_COMMON_WORDS_ATOMS = true
//		query = "Школа А+";
//		query = "школа №25"; // test '№25', '25'? -- 'школа', 'школа №25', 'школа 25'
//		query = "ВЕЛОwatt";
//		query = "O128894."; // FIX Osm id getOsmIdFromMapObjectId
		// 'M 2' variations data: 'M-2', 'M 2' and '2 M' 
		// POI М-2    (306998303): + ('M-2', 'M 2', '2 M')  - ('2M', 'M2', '2-M')
		// POI '2 M' (3869587585): + ('M-2', 'M 2', '2 M')  - ('2M', 'M2', '2-M') - 2 is not indexed query 2M, 2-M
		// m-n Topol 2(120393782): + ('M-2', 'M 2', '2 M')  - ('2M', 'M2', '2-M')
//		query = "2-M";
		// '2XU', '2X.' 
//		query = "360692"; // refs - 3г (not indexed, search by 3 3gh) 390094/5536x/4267x  
		
//		pattern = "Belarus_minsk";
//		query = "Независим. 48, 1";
		
//		pattern = "Australia";
//		query = "Holmby road 18 B"; // 'Holmby 18 B', 'Holmby 18-B', 'Holmby 18B'
//		query = "Holmby Melbourne 18B";
		
//		pattern = "Us_new-york_new"; // new-york, new-jersey
//		pattern = "Us_new-"; 
//		location = new LatLon(40.64946, -74.00682); // loaded
//		location = new LatLon(40.760536, -73.99043);
//		location = new LatLon(40.64946, -73.50682);
//		query = "New York The plaza";
//		query = "New York plaza";
//		query = "New York st"; // 'NY s.' - 0.5s 100k, 'NY st' - 2s (700k)
//		query = "New York 4 av 8"; // unit test '4th av', '4 ave', '4th avenue' 241843204 brooklyn - not 48
//		query = "New York 4 av 8"; // 160947243
//		query = "4th ave"; //  unit '4 ave'   
//		query = "blvd"; //  unit test  'blvd', 'boulevard' - 248280132
		
		
//		pattern = "France_ile-de-france_eu";
//		query = "Rue Bouchardon 2BIS"; // '2bis' OK, '2 BIS' OK , '2' OK, '2-BIS'
//		query = "Rue Jean Poulmarch 17bis"; //  17bis OK, 17 OK, 17 BIS - OK 'Rue Jean Poulmarch 17;17 bis' 
//		query = "Dieu 8-bis"; // 'Rue Dieu 8 bis' , '8-bis', '8 bis' 

		
//		pattern = "World_basemap_2";
//		pattern2 = "Ukraine";
//		query = "о. Пасхи"; // o
//		query = "остров Пасхи"; // o. -> остров - not supported data need to be updated
//		query = "New york";
//		query  = "Madeira"; // short_name	Madeira
//		query  = "Everest";
//		query  = "Rio de Janeiro";

//		pattern = "Spain_aragon_europe_";
//		query = "Basílica de Nuestra Señora del Pilar";
//		query = "Catedral-Basílica de Nuestra Señora del Pilar"; // 7 words! 2^7 combinations
		
//		pattern = "Peru_";
//		query ="Calle 20 188 San Isidro Lima"; // 1430799557
//		query ="Lima Calle 20 San Isidro";
//		query ="Calle 20 ";

		long t = System.nanoTime();

		List<BinaryMapIndexReader> ls = new ArrayList<BinaryMapIndexReader>();
		for (File f : folder.listFiles()) {
			if (f.getName().startsWith(pattern) || f.getName().startsWith(pattern2)) {
				SpatialTextSearch.initFile(ls, f);
			} else if(f.getName().equals(OsmandRegions.REGIONS_OCBF)){
				SpatialTextSearch.initFile(ls, f);
			}
		}
		SpatialTextSearch a = new SpatialTextSearch();
		System.out.println(String.format("Index files %.1f ms", (System.nanoTime() - t) / 1e6));

		SpatialSearchContext searchContext = new SpatialSearchContext(new SpatialTextSearchSettings(), ls, location);
		SpatialSearchResults rs = a.searchTest(query, searchContext);
		SpatialSearchResult mainResult = rs.getFirstResult();
		if (mainResult != null && mainResult.matchedTokens() < rs.tokens.size() - 2) {
			// another way to check to check to get mainResult - boundary object
			City bbox = null;
			for (MapObject o : mainResult.getObjects()) {
				if (o instanceof City c && c.getBbox31() != null) {
					// check that city is not inside maps searched
					bbox = c;
					break;
				}
			}
			if (bbox != null) {
				System.out.println("Suggest search other region - " + bbox);
			}
		}
		SpatialTextSearchSettings settings = new SpatialTextSearchSettings();
		settings.ALWAYS_READ_COMMON_WORDS_ATOMS = true;
		searchContext = new SpatialSearchContext(settings, ls, location);
		a.searchTest(query, searchContext);
	}
}
