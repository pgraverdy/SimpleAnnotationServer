package uk.org.llgc.annotation.store;

import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Before;
import org.junit.After;
import org.junit.rules.TemporaryFolder;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import uk.org.llgc.annotation.store.adapters.StoreAdapter;
import uk.org.llgc.annotation.store.AnnotationUtils;

import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.Lang;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.query.* ;

public class TestPublish {
	protected AnnotationUtils _annotationUtils = null;
	protected StoreAdapter _store = null;
	//@Rule
	protected File _testFolder = null;

	public TestPublish() throws IOException {
		super();
		_testFolder = new File(new File(getClass().getResource("/").toString()),"tmp");
		_annotationUtils = new AnnotationUtils(new File(getClass().getResource("/contexts").getFile()));
	}
	@Before 
   public void setup() throws IOException {
		Map<String,String> tProps = new HashMap<String,String>(); 
		tProps.put("store","jena");
		File tDataDir = new File(_testFolder, "data");
		tDataDir.mkdirs();
		tProps.put("data_dir",tDataDir.getPath());
		tProps.put("baseURI","http://dev.llgc.org.uk/annotation/");

		StoreConfig tConfig = new StoreConfig(tProps);
		StoreConfig.initConfig(tConfig);
		_store = StoreConfig.getConfig().getStore();
	}

   @After
   public void tearDown() {
	}

	@Test
	public void testPublish() throws IOException {
		List<Map<String, Object>> tAnnotationListJSON = _annotationUtils.readAnnotationList(new FileInputStream(getClass().getResource("/jsonld/testAnnotationList1.json").getFile()), StoreConfig.getConfig().getBaseURI(null)); //annotaiton list

		List<Model> tAnnosAsModel = _store.addAnnotationList(tAnnotationListJSON);
		// add models to single model and use sparql or something to test for valid content
		Model tMasterModel = tAnnosAsModel.get(0);
		String tOtherId = "";
		String tKnownID = "http://example.com/annotation/1";
		for (int i = 1; i < tAnnosAsModel.size(); i++) {
			StmtIterator tResults = tAnnosAsModel.get(i).listStatements(null,tMasterModel.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),tMasterModel.createResource("http://www.w3.org/ns/oa#Annotation"));
			Statement tResult = tResults.nextStatement();
			String tId = tResult.getSubject().toString();
			if (!tId.equals(tKnownID)) {
				tOtherId = tId;
			}

			tMasterModel.add(tAnnosAsModel.get(i));
		}

		this.testAnnotation(tMasterModel, tKnownID, "Test content 1","http://example.com/image1#xywh=0,132,102,10"); 
		this.testAnnotation(tMasterModel, tOtherId, "Test Content 2","http://example.com/image1#xywh=1873,132,102,10"); 
	}


	@Test
	public void testCreate() throws IOException {
		Map<String, Object> tAnnotationJSON = _annotationUtils.readAnnotaion(new FileInputStream(getClass().getResource("/jsonld/testAnnotation.json").getFile()), StoreConfig.getConfig().getBaseURI(null)); 

		Model tModel = _store.addAnnotation(tAnnotationJSON);
		
		this.testAnnotation(tModel, "Bob Smith","http://dev.llgc.org.uk/iiif/examples/photos/canvas/3891216.json#xywh=5626,1853,298,355"); 
	}

	// test reuse of id
	@Test
	public void testDelete() throws IOException {
		Map<String, Object> tAnnotationJSON = _annotationUtils.readAnnotaion(new FileInputStream(getClass().getResource("/jsonld/testAnnotation.json").getFile()), StoreConfig.getConfig().getBaseURI(null)); 

		Model tModel = _store.addAnnotation(tAnnotationJSON);
		Iterator<Resource> tSubjects = tModel.listSubjects();
		Resource tAnnoId = null;
		while (tSubjects.hasNext()) {
			Resource tSubject = tSubjects.next();
			if (tSubject.getURI() != null && tSubject.getURI().contains("http://")) {
				tAnnoId = tSubject;
				break;
			}
		}
		_store.deleteAnnotation(tAnnoId.getURI());
		//RDFDataMgr.write(System.out, tDelModel, Lang.NQUADS);
		assertNull("Annotation should be deleted but it isn't.", _store.getAnnotation(tAnnoId.getURI()));
	}

	@Test
	public void testUpdate() throws IOException {
		Map<String, Object> tAnnotationJSON = _annotationUtils.readAnnotaion(new FileInputStream(getClass().getResource("/jsonld/testAnnotation.json").getFile()), StoreConfig.getConfig().getBaseURI(null)); 

		_store.addAnnotation(tAnnotationJSON);

		((Map<String,Object>)tAnnotationJSON.get("resource")).put("chars","<p>New String</p>");

		Model tModel = _store.addAnnotation(tAnnotationJSON);

		this.testAnnotation(tModel, "New String","http://dev.llgc.org.uk/iiif/examples/photos/canvas/3891216.json#xywh=5626,1853,298,355"); 
	}

	@Test
	public void testPage() throws IOException {
		List<Map<String, Object>> tAnnotationList = _annotationUtils.readAnnotationList(new FileInputStream(getClass().getResource("/jsonld/testAnnotationList2.json").getFile()), StoreConfig.getConfig().getBaseURI(null)); 

		for (Map<String,Object> tAnnotation : tAnnotationList) {
			_store.addAnnotation(tAnnotation);
		}

		List<Model> tAnnotationsModel = _store.getAnnotationsFromPage("http://example.com/image2"); 
		Model tModel = ModelFactory.createDefaultModel();
		for (Model tModelAnno : tAnnotationsModel) {
			tModel.add(tModelAnno);
		}

		this.testAnnotation(tModel, "http://example.com/annotation/2", "Test content 1a","http://example.com/image2#xywh=0,132,102,10"); 
		this.testAnnotation(tModel, "http://example.com/annotation/3", "Test Content 2a","http://example.com/image2#xywh=1873,132,102,10"); 
	}

	@Test
	public void testUTF8() throws IOException {
		Map<String, Object> tAnnotationJSON = _annotationUtils.readAnnotaion(new FileInputStream(getClass().getResource("/jsonld/utf-8.json").getFile()), StoreConfig.getConfig().getBaseURI(null)); 

		Model tModel = _store.addAnnotation(tAnnotationJSON);

		this.testAnnotation(tModel, "http://example.com/annotation/utf-8", new String("UTF 8 test â".getBytes("UTF8"),"UTF8"),"http://dev.llgc.org.uk/iiif/examples/photos/canvas/3891217.json#xywh=5626,1853,298,355"); 
	}

	protected void testAnnotation(final Model pModel, final String pValue, final String pTarget) {
		String tQuery = "PREFIX oa: <http://www.w3.org/ns/oa#> PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> PREFIX cnt: <http://www.w3.org/2011/content#> select ?content ?uri ?fragement where { ?annoId oa:hasTarget ?target . ?target oa:hasSource ?uri . ?target oa:hasSelector ?fragmentCont . ?fragmentCont rdf:value ?fragement . ?annoId oa:hasBody ?body . ?body cnt:chars ?content }";
		this.queryAnnotation(pModel,tQuery, pValue, pTarget);
	}

	protected void testAnnotation(final Model pModel, final String pId, final String pValue, final String pTarget) {
		String tQuery = "PREFIX oa: <http://www.w3.org/ns/oa#> PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> PREFIX cnt: <http://www.w3.org/2011/content#> select ?content ?uri ?fragement where { <$id> oa:hasTarget ?target . ?target oa:hasSource ?uri . ?target oa:hasSelector ?fragmentCont . ?fragmentCont rdf:value ?fragement . <$id> oa:hasBody ?body . ?body cnt:chars ?content }".replaceAll("\\$id",pId);
		this.queryAnnotation(pModel, tQuery, pValue, pTarget);
	}	

	protected ResultSet queryAnnotation(final Model pModel, final String pQuery, final String pValue, final String pTarget) {
		Query query = QueryFactory.create(pQuery) ;
		ResultSetRewindable results = null;
		try (QueryExecution qexec = QueryExecutionFactory.create(query,pModel)) {
		
			results = ResultSetFactory.copyResults(qexec.execSelect());
			for ( ; results.hasNext() ; )
			{
				QuerySolution soln = results.nextSolution() ;
				assertEquals("Content doesn't match.", "<p>" + pValue + "</p>", soln.getLiteral("content").toString());

				String tURI = soln.getResource("uri").toString() + "#" + soln.getLiteral("fragement");
				assertEquals("Target doesn't match", pTarget, tURI);
			}
		}
		results.reset();
		return results;
	}

	protected Statement matchesValue(final Model pModel, final Resource pResource, final String pProp, final String pValue) {
		StmtIterator tResults = pModel.listStatements(pResource, pModel.createProperty(pProp), (Resource)null);
		assertTrue("Missing " + pProp + " for resource " + pResource.getURI(), tResults.hasNext());
		Statement tResult = tResults.nextStatement();
		assertEquals("Value mismatch", pValue, tResult.getString());

		return tResult;
	}

	protected Statement matchesValue(final Model pModel, final Resource pResource, final String pProp, final Resource pValue) {
		StmtIterator tResults = pModel.listStatements(pResource, pModel.createProperty(pProp), (Resource)null);
		assertTrue("Missing " + pProp + " for resource " + pResource.getURI(), tResults.hasNext());
		Statement tResult = tResults.nextStatement();
		assertEquals("Value mismatch", pValue.getURI(), tResult.getResource().getURI());

		return tResult;
	}
}
