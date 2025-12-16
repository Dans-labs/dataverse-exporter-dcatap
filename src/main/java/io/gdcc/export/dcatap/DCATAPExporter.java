package io.gdcc.export.dcatap;

import com.google.auto.service.AutoService;
import io.gdcc.spi.export.ExportDataProvider;
import io.gdcc.spi.export.ExportException;
import io.gdcc.spi.export.Exporter;
import io.gdcc.spi.export.XMLExporter;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.MediaType;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
// Maybe use vocabs, but for now just use strings which I like more
//import org.apache.jena.vocabulary.RDFS;

/**
 * An Exporter that creates DCAT-AP metadata from a Dataverse dataset.
 * https://semiceu.github.io/DCAT-AP/releases/3.0.0/#Dataset
 * 
 * Where possible we try to add all Datavesre metadata to the DCAT-AP output.
 * Also we try te be compliant with other EU varieties of DCAT-AP where possible.  
 * Sometimes by adding bogus values line "Unknown", "None" or "not available".
 */
@AutoService(Exporter.class)
public class DCATAPExporter implements XMLExporter {
//public class DCATAPExporter implements Exporter { 
    static String DCAT = "http://www.w3.org/ns/dcat#";
    static String DCT = "http://purl.org/dc/terms/";
    static String RDFS = "http://www.w3.org/2000/01/rdf-schema#";
    static String DCATAP = "http://data.europa.eu/r5r/";
    static String VCARD = "http://www.w3.org/2006/vcard/ns#";
    static String FOAF = "http://xmlns.com/foaf/0.1/";
    static String SPDX = "http://spdx.org/rdf/terms#";
    static String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    
    // Use this for testing different output formats ONLY!
    // unfortunately, it is just a string, so no enum or such
    private String outputLang = ""; // default output format (RDF/XML) if empyty!

    public String getOutputLang() {
        return outputLang;
    }

    public void setOutputLang(String outputLang) {
        this.outputLang = outputLang;
    }
    
    /**
     * The name of the format it creates. If this format is already provided by a built-in exporter,
     * this Exporter will override the built-in one. (Note that exports are cached, so existing
     * metadata export files are not updated immediately.)
     */
    @Override
    public String getFormatName() {
        return "dcat_ap";
    }

    /**
     * The display name shown in the UI.
     *
     * @param locale Used to generate a translation.
     */
    @Override
    public String getDisplayName(Locale locale) {
        return "DCAT-AP";
    }

    /** Whether the exported format should be available as an option for Harvesting. */
    @Override
    public Boolean isHarvestable() {
        return true; // For the RDF XML we can have it harvestable
    }

    /** Whether the exported format should be available for download in the UI and API. */
    @Override
    public Boolean isAvailableToUsers() {
        return true;
    }

    @Override
    public String getXMLNameSpace() {
        return "http://www.w3.org/1999/02/22-rdf-syntax-ns# http://purl.org/dc/terms/ http://spdx.org/rdf/terms# http://www.w3.org/1999/02/22-rdf-syntax-ns# http://data.europa.eu/r5r/ http://www.w3.org/2006/vcard/ns# http://www.w3.org/ns/dcat# http://www.w3.org/2000/01/rdf-schema# http://xmlns.com/foaf/0.1/";
    }

    @Override
    public String getXMLSchemaLocation() {
        return "";
    }

    @Override
    public String getXMLSchemaVersion() {
        return "";
    }

    /**
     * Defines the mime type of the exported format. Used when metadata is downloaded, i.e. to
     * trigger an appropriate viewer in the user's browser.
     */
    @Override
    public String getMediaType() {
        String type = MediaType.APPLICATION_XML; // default RDF/XML
        // Note that we need  XML in the OAI-PMH harvesting output!
        
        switch (outputLang) {
            case "RDF/XML":
                type = MediaType.APPLICATION_XML;
                break;
            case "TURTLE":
                type = MediaType.TEXT_PLAIN; // there is nothing like text/turtle in MediaType!
                break;
            case "JSON-LD":
                type = MediaType.APPLICATION_JSON;
                break;
            default:
                // default to RDF/XML
                break;
        }
        return type;
    }
    
    /**
     * This method is called by Dataverse when metadata for a given dataset in this format is
     * requested.
     */
    @Override
    public void exportDataset(ExportDataProvider dataProvider, OutputStream outputStream)
            throws ExportException {
        try {
            JsonObject datasetJson = dataProvider.getDatasetJson();
            // Note that we could try to use some of those other providers to retrieve metadata as well
            // ORE might be interesting, but for now stick to the JSON only
            
            Model model = createRDFModelFromDatasetJson(datasetJson);
            
            if ( outputLang.isEmpty()) {
                model.write(outputStream);
            } else {
                model.write(outputStream, outputLang);
            }
            
            // Note: how could we support these different output types using this same exporter?
            //model.write(outputStream); // THIS always works, defaults to RDF/XML
 
            // Try some others
            //RDFJSONWriter.output(outputStream, model.getGraph()); // works, but no context!
            //model.write(outputStream, "RDF/XML"); // also works, but that was the default anyway
            //model.write(outputStream, "TURTLE"); // org.apache.jena.shared.NoWriterForLangException: Writer not found: TURTLE
            // at org.apache.jena.rdf.model.impl.RDFWriterFImpl.getWriter(RDFWriterFImpl.java:66)
            //
            //RDFDataMgr.write(outputStream, model, RDFFormat.TURTLE);
            // here I got: 
            // Caused by: java.lang.NoSuchMethodError: 'java.lang.String org.apache.jena.atlas.lib.Lib.lowercase(java.lang.String)'
            //        at org.apache.jena.riot.RDFLanguages.canonicalKey(RDFLanguages.java:470)
            //
            //RDFWriter.source(model.getGraph()).format(RDFFormat.JSONLD).output(outputStream);            
            //RDFDataMgr.write(outputStream, model, RDFFormat.JSONLD);
            //RDFDataMgr.write(outputStream, model, Lang.JSONLD11);
            //
            // These work at build time, but fail when the jar is deployed as plugin in Dataverse:
            //model.write(outputStream, "JSON-LD"); // other exporters use this too
            //model.write(outputStream, "RDF/XML"); // when for OAI harvesting, XML fits best
            //model.write(outputStream, "TURTLE"); // human-readable text format, best for debugging
            
            // Flush the output stream - The output stream is automatically closed by
            // Dataverse and should not be closed in the Exporter.
            outputStream.flush();
        } catch (Exception ex) {
            System.out.println("Exception caught in DCAT-AP exporter. Printing stacktrace...");
            ex.printStackTrace();
            // If anything goes wrong, an Exporter should throw an ExportException.
            throw new ExportException("Unknown exception caught during export: " + ex);
        }
    }
    
    Model createRDFModelFromDatasetJson(JsonObject datasetJson) {
        Model model = ModelFactory.createDefaultModel();
        // The RDF stuff using Apache Jena

        // make the model use prefixes
        model.setNsPrefix("dcat", DCAT);
        model.setNsPrefix("dct", DCT);
        model.setNsPrefix("rdfs", RDFS);
        model.setNsPrefix("dcatap", DCATAP);
        model.setNsPrefix("vcard", VCARD);
        model.setNsPrefix("foaf", FOAF);
        model.setNsPrefix("spdx", SPDX);
        // model.setNsPrefix("rdf", RDF); // we do not need to define rdf prefix ourselves
        
        String identifier = datasetJson.getString("identifier", "");
        // note that with protocol and authority we can build a persistent URL as well
        String persistentURL = datasetJson.getString("persistentUrl", "");
        // Use the persistentUrl  instead
        // schema.org export is doing that as well, so that makes it a bit consistent
        // add the dcat ap dataset to the model
        Resource datasetModel = model.createResource(persistentURL);
        
        // Note that this is not the DCT type, but the RDF type
        datasetModel.addProperty(model.createProperty(RDF, "type"), model.createResource(DCAT + "Dataset"));

        //--- 
        // DCAT-AP Dataset Property: landing page
        datasetModel.addProperty(
                model.createProperty(DCAT, "landingPage"),
                model.createResource(persistentURL));
        
        
        //---
        // DCAT-AP Dataset Property: applicable legislation
        // 
        // applicableLegislation, only mandatory for HealthDCAT-AP, so skipping for now
        // dcatap:applicableLegislation <http://data.europa.eu/eli/reg/2022/868/oj>;
        //datasetModel.addProperty(model.createProperty(DCATAP, "applicableLegislation"), "http://data.europa.eu/eli/reg/2022/868/oj");

        //--- 
        // DCAT-AP Dataset Property: identifier
        datasetModel.addProperty(model.createProperty(DCT, "identifier"), persistentURL);
        
        // drill down to some useful objects
        JsonObject datasetVersion = datasetJson.getJsonObject("datasetVersion");

        //--- 
        // DCAT-AP Dataset Property: version
        // get version number and minor version number
        // note that the citation uses a 'V' before the version number, 
        // so we do that here as well
        int versionNumber = datasetVersion.getInt("versionNumber");
        int versionMinorNumber = datasetVersion.getInt("versionMinorNumber");
        String versionInfo = String.format("V%d.%d", versionNumber, versionMinorNumber);

        datasetModel.addProperty(model.createProperty(DCAT, "version"), versionInfo);

        
        // get citation metadata block, with most important metadata
        JsonObject citationBlock = datasetVersion.getJsonObject("metadataBlocks").getJsonObject("citation");
        // get the fields array from citation block
        JsonArray citationFields = citationBlock.getJsonArray("fields");

        // absolute minimal for DCAT-AP is title and description
        // mandatory for DCAT-AP compliance

        //---
        // DCAT-AP Dataset Property: title
        String title = getPrimitiveValueFromFieldsByTypeName(citationFields, "title", "no-title");
        datasetModel.addProperty(model.createProperty(DCT, "title"), model.createLiteral(title, "en"));

        //---
        // DCAT-AP Dataset Property: description 
        String description = "no-description"; // provide default, because mandatory 
        JsonArray dsDescriptions = getValuesFromCompoundFieldByTypeName(citationFields, "dsDescription");
        // find the first dsDescriptionValue
        for (int i = 0; i < dsDescriptions.size(); i++) {
            JsonObject dsDescription = dsDescriptions.getJsonObject(i);
            JsonObject dsDescriptionValue = dsDescription.getJsonObject("dsDescriptionValue");
            if (dsDescriptionValue != null) {
                description = dsDescriptionValue.getString("value", "no-description");
                break;
            }
        }
        datasetModel.addProperty(model.createProperty(DCT, "description"), model.createLiteral(description, "en"));
        
        // add more metadata from citationFields
        
        //--- 
        // DCAT-AP Dataset Property: release date
        String pubDate = datasetVersion.getString("publicationDate", "no-publication-date");
        // check if we can parse the date properly?
        datasetModel.addProperty(model.createProperty(DCT, "issued"), model.createTypedLiteral(pubDate, XSDDatatype.XSDdate));
        
        //---
        // DCAT-AP Dataset Property: modification date
        // lastUpdateTime is actually the same if the Dataset is published, but...
        String lastUpdateTime = datasetVersion.getString("lastUpdateTime", "no-last-update-time");
        // parse lastUpdateTime to proper date object
        String formattedLastUpdateTime = lastUpdateTime;
        try {
            LocalDateTime parsedDateTime = LocalDateTime.parse(lastUpdateTime, DateTimeFormatter.ISO_DATE_TIME);
            formattedLastUpdateTime = parsedDateTime.format(DateTimeFormatter.ISO_DATE);
        } catch (Exception e) {
            System.out.println("Failed to parse lastUpdateTime: " + lastUpdateTime);
        }
        datasetModel.addProperty(model.createProperty(DCT, "modified"), model.createTypedLiteral(formattedLastUpdateTime, XSDDatatype.XSDdate));
        
        //---
        // DCAT-AP Dataset Property: provenance
        // we do not have any explicit provenance information in Dataverse for now
        // it is not mandatory in DCAT-AP
        //        Resource provenanceStatement =  model.createResource()
        //                        .addProperty(model.createProperty(RDFS, "type"), "dct:ProvenanceStatement")
        //                        .addProperty(
        //                                model.createProperty(RDFS, "label"),
        //                                model.createLiteral(
        //                                        "No specific information about how the data was collected", "en"));
        //        datasetModel.addProperty(model.createProperty(DCT, "provenance"), provenanceStatement);
        
        //---
        // DCAT-AP Dataset Property: publisher
        // Publisher would be the organisation behind the Dataverse installation itself
        // maybe we can make the plugin configurable to set this properly?
        // This is mandatory for DCAT-AP-NL, not for DCAT-AP, so skipping for now
        //
        // The SchemaDotOrg exporter has some information, but it is using the collection information (root level). 
        // not sure if that is the best option here?
        //        Resource publisher = model.createResource()
        //                .addProperty(model.createProperty(RDFS, "type"), "foaf:Agent")
        //                .addProperty(model.createProperty(RDFS, "type"), "foaf:Organization")
        //                .addProperty(
        //                        model.createProperty("http://xmlns.com/foaf/0.1/name"),
        //                        model.createLiteral("DANS Data Station Life Sciences", "en"))
        //                .addProperty(
        //                        model.createProperty(VCARD, "hasURL"),
        //                        "http://dans.knaw.nl");
        //        datasetModel.addProperty(model.createProperty(DCT, "publisher"), publisher);

        //---
        // DCAT-AP Dataset Property: contact point
        JsonArray contactPoints = getValuesFromCompoundFieldByTypeName(citationFields, "datasetContact");
        Resource contactPoint = createContactPoint(model, contactPoints);
        datasetModel.addProperty(model.createProperty(DCAT, "contactPoint"), contactPoint);

        //---
        // DCAT-AP Dataset Property: creator
        // Creators are the authors from citationFields
        JsonArray authors = getValuesFromCompoundFieldByTypeName(citationFields, "author");
        for (int i = 0; i < authors.size(); i++) {
            JsonObject authorObj = authors.getJsonObject(i);
            Resource creatorResource = createCreator(model, authorObj);
            datasetModel.addProperty(model.createProperty(DCT, "creator"), creatorResource);
        }

        // DON'T think this is in DCAT-AP
        // alternative title, is alternativeTitle multiple primitive
        //        List<String> altTitles = getPrimitiveValueArrayFromFieldsByTypeName(citationFields, "alternativeTitle");
        //        for (String altTitleValue : altTitles) {
        //                datasetModel.addProperty(
        //                        model.createProperty(DCT, "alternative"),
        //                        model.createLiteral(altTitleValue, "en"));
        //        }
        
        //---
        // DCAT-AP Dataset Property: language
        // Determine the dataset language, if any
        // Note that it is not the same as the metadata language, which is now assumed default '@en' !
        // also there is a multi-values fields, we can have multiple languages and the list of code is huge
        // and most like need mapping to something useful for DCAT-AP
        //String language = getPrimitiveValueFromFieldsByTypeName(citationFields, "language", "");
        List<String> languages = getValueArrayFromMultipleFieldByTypeName(citationFields, "language");
        for (String langValue : languages) {
            // for now just add the literal value as is
            datasetModel.addProperty(
                    model.createProperty(DCT, "language"),
                    model.createLiteral(langValue, "en"));
        }
        // what if we have no language at all? then we skip it.
        // if it is mandatory we could add "Unknown"@en or similar?
        
        //---
        // DCAT-AP Dataset Property: access rights
        // accessRights is mandatory for DCAT-AP-NL, not for DCAT-AP, so skipping for now
        //dct:accessRights   <http://publications.europa.eu/resource/authority/access-right/PUBLIC>;
        // need to detect if there are any access restrictions from the dataset json?
        
        //---
        // DCAT-AP Dataset Property: theme 
        // dcat:theme <http://publications.europa.eu/resource/authority/data-theme/HEAL> ;
        // probably some mapping from subjects to data-themes needed here
        // only for Health it is mandatory!
        //datasetModel.addProperty(model.createProperty(DCAT, "theme"), "http://publications.europa.eu/resource/authority/data-theme/HEAL");
        // if we have subject:'Medicine, Health and Life Sciences',	we can map to HEAL, and we could do HealthCDAT-AP
        // otherwise mapping seems useless for now. 
        //
        // Since it is research data you could argue it always also with an educational purpose?
        // http://publications.europa.eu/resource/authority/data-theme/EDUC
        
        //---
        // DCAT-AP Dataset Property: keyword
        // keywords would be good, if we have them
        JsonArray keywords = getValuesFromCompoundFieldByTypeName(citationFields, "keyword");
        for (int i = 0; i < keywords.size(); i++) {
            JsonObject keywordObj = keywords.getJsonObject(i);
            JsonObject keywordValueObj = keywordObj.getJsonObject("keywordValue");
            if (keywordValueObj != null) {
                String keywordValue = keywordValueObj.getString("value", "");
                // Note that keywords kan have URI's when an CVOC is used, but for now just use the literal value
                if (!keywordValue.isEmpty()) {
                    datasetModel.addProperty(
                            model.createProperty(DCAT, "keyword"),
                            model.createLiteral(keywordValue, "en"));
                }
            }
        }
        // subjects as dct:subject, could also be added as a keyword
        // there is no subject in DCAT-AP, only keywords
        // however, what if it just was 'Other', we could skip that as a keyword I think ?

        // DCAT-AP Dataset Property not used and mentioned above yet, but could maybe be added:
        //    documentation 
        //    frequency 
        //    geographical coverage 
        //    has version 
        //    in series 
        //    is referenced by 
        //    other identifier 
        //    qualified attribution 
        //    qualified relation 
        //    related resource 
        //    sample 
        //    source 
        //    spatial resolution 
        //    temporal coverage 
        //    temporal resolution 
        //    type 
        //    conforms to 
        //    version notes 
        //    was generated by
        
        
        // Note: we could try to determine dataset license, and somehow add it to each file distribution as well
        // we have the name and the uri in the license object, but can we map that?
        // just the URI, but note that we do not have license on dataset level, only on file level
        JsonObject licenseObj = datasetVersion.getJsonObject("license");
        
        //---
        // DCAT-AP Dataset Property: dataset distribution
        // find any files and add them as distributions
        // Note that dcat-ap there should be at least one file/distribution, 
        // but Dataverse does not force that!
        JsonArray files = datasetVersion.getJsonArray("files");
        for (int i = 0; i < files.size(); i++) {
            JsonObject fileObj = files.getJsonObject(i);
            // Needed to pass license, because is only on dataset level
            Resource distribution = createFileDistribution(model, fileObj, licenseObj);
            // add the accessURL to the distribution, using the dataset persistent URL
            //---
            // DCAT-AP Distribution Property: access URL
            distribution.addProperty(model.createProperty(DCAT, "accessURL"), persistentURL);
            
            // link the distribution to the dataset
            datasetModel.addProperty(model.createProperty(DCAT, "distribution"), distribution);
        }
        
        return model;
    }
    
    Resource createFileDistribution(Model model, JsonObject fileObj, JsonObject licenseObj) {
        JsonObject dataFile = fileObj.getJsonObject("dataFile");
        
        // could use the persistenId if we have one for the file
        int id = dataFile.getInt("id", 0);
        // the schema.org export uses http://localhost:8080/api/access/datafile, so we can do the same here
        Resource distribution = model.createResource("http://localhost:8080/api/access/datafile/" + id);
        //Resource distribution = model.createResource();
        
        distribution.addProperty(model.createProperty(RDF, "type"), model.createResource(DCAT + "Distribution"));

        //---
        // DCAT-AP Distribution Property: download URL
        //String downloadUrl = dataFile.getString("downloadUrl", "no-download-url");
        // accessURL is added later, when we have the dataset persistent URL, 
        // there is no download URL for a file alone, maye via that optional persistenId?
        
        //---
        // DCAT-AP Distribution Property: title
        String fileName = dataFile.getString("filename", "no-filename");
        distribution.addProperty(model.createProperty(DCT, "title"), fileName);
        
        //---
        // DCAT-AP Distribution Property: rights
        // if restricted, do something special?
        Boolean restricted = fileObj.getBoolean("restricted");   
        // dct:rights , restricted or not?
        // dct:rights        <http://publications.europa.eu/resource/authority/access-right/PUBLIC>;
        // no specific terms used for now
        if (restricted != null && restricted) {
            distribution.addProperty(
                    model.createProperty(DCT, "rights"),
                    model.createResource("http://publications.europa.eu/resource/authority/access-right/RESTRICTED"));
        } else {
            distribution.addProperty(
                    model.createProperty(DCT, "rights"),
                    model.createResource("http://publications.europa.eu/resource/authority/access-right/PUBLIC"));
        }
        // Note: what are we going to do with embargo and or retentionPeriod?
        
        // DON'T see this in DCAT-AP !
        // type is not mandatory for DCAT-AP, so skipping for now
        // we always have here DOWNLOADABLE_FILE, even if we cannot really download it?
        //distribution.addProperty(model.createProperty(DCT, "type"),
        //        "http://publications.europa.eu/resource/authority/distribution-type/DOWNLOADABLE_FILE");
        
        //---
        // DCAT-AP Distribution Property: rbyte size
        int bytesize = dataFile.getInt("filesize", 0);
        //distribution.addProperty(model.createProperty(DCAT, "byteSize"), String.valueOf(bytesize) );
        distribution.addProperty(model.createProperty(DCAT, "byteSize"), model.createTypedLiteral(bytesize));
        
        //---
        // DCAT-AP Distribution Property: description
        // NOTE: is not mandatory for DCAT-AP but it is for variants like DCAT-AP-NL
        // so we add it here anyway
        // provide a default; dct:description "No specific description available"@en; or  simply "None"@en
        String description = dataFile.getString("description", "No specific description available");
        if (!description.isEmpty()) {
            distribution.addProperty(
                    model.createProperty(DCT, "description"),
                    model.createLiteral(description, "en"));
        }
        
        //---
        // DCAT-AP Distribution Property: format
        // DCT format; use MIME type from contentType, but that would need some mapping
        // use mediatype from IANA
        String mimeType = dataFile.getString("contentType", "application/octet-stream");
        distribution.addProperty(
                model.createProperty(DCAT, "mediaType"),
                "http://www.iana.org/assignments/media-types/" + mimeType);
        
        //---
        // DCAT-AP Distribution Property: checksum
        // checksum would be nice to have as well
        JsonObject checksumObj = dataFile.getJsonObject("checksum");
        if (checksumObj != null) {
            String checksumValue = checksumObj.getString("value", "");
            String checksumType = checksumObj.getString("type", "MD5"); // default to MD5
            String checksumAlgorithm = getSPDXChecksumAlgorithmURI(checksumType);
            if (!checksumAlgorithm.isEmpty() && !checksumValue.isEmpty()) {
                Resource checksumResource = model.createResource()
                        .addProperty(
                                model.createProperty(SPDX, "algorithm"),
                                checksumAlgorithm)
                        .addProperty(
                                model.createProperty(SPDX, "checksumValue"),
                                checksumValue);
                distribution.addProperty(model.createProperty(SPDX, "checksum"), checksumResource);
            }
        }
        
        //---
        // DCAT-AP Distribution Property: licence
        // would be nice to have license on distribution, 
        // but dataverse now only supports license on dataset level
        if (licenseObj != null) {
            String licenseURI = licenseObj.getString("uri", "");
            if (!licenseURI.isEmpty()) {
                distribution.addProperty(model.createProperty(DCT, "license"), model.createResource(licenseURI));
                
            } else {
                String licenseName = licenseObj.getString("name", "");
                if (!licenseName.isEmpty()) {
                    distribution.addProperty(
                            model.createProperty(DCT, "license"),
                            model.createLiteral(licenseName, "en"));
                }
            }
        }

        // DCAT-AP Distribution Property not used and mentioned above yet, but could maybe be added:
        //    access service
        //    applicable legislation
        //    availability
        //    compression format
        //    documentation
        //    has policy
        //    language
        //    linked schemas
        //    media type
        //    modification date
        //    packaging format
        //    release date
        //    spatial resolution
        //    status
        //    temporal resolution    
        
        return distribution;
    }
    
    // return the algorithm for SPDX based on the type string from Dataverse
    String getSPDXChecksumAlgorithmURI(String type) {
        // note that Dataverse StandardSupportedAlgorithms use a minus with SHA
        switch (type.toUpperCase()) {
            case "MD5":
                return "checksumAlgorithm_md5";
            case "SHA-1":
                return "checksumAlgorithm_sha1";
            case "SHA-224": 
                return "checksumAlgorithm_sha224";
            case "SHA-256":
                return "checksumAlgorithm_sha256";
            case "SHA-512":
                return "checksumAlgorithm_sha512";
            default:
                return ""; // empty indicates we do not have a mapping
        }
    }
    
    Resource createCreator(Model model, JsonObject author) {
        Resource creatorResource = model.createResource();
        //creatorResource.addProperty(model.createProperty(RDFS, "type"), model.createResource(FOAF + "Agent"));
        // assume person for now - could be organisation as well?
        creatorResource.addProperty(model.createProperty(RDFS, "type"), model.createResource(FOAF + "Person"));
        
        JsonObject authorName = author.getJsonObject("authorName");
        if (authorName != null) {
            String authorNameValue = authorName.getString("value", "");
            // But what if it is a ORCID or other autor identifier?
            creatorResource.addProperty(
                    model.createProperty(FOAF, "name"),
                    model.createLiteral(authorNameValue, "en"));
        }
        JsonObject authorAffiliation = author.getJsonObject("authorAffiliation");
        if (authorAffiliation != null) {
            String authorAffiliationValue = authorAffiliation.getString("value", "");
            if (!authorAffiliationValue.isEmpty()) {
                // vcard for affiliation, supposed to be organization-name
                // But what if it is a ROR or other organisation identifier?
                // expandedvalue.termName could be used for getting the humanreadable name
                creatorResource.addProperty(
                        model.createProperty(VCARD, "organization-name"),
                        model.createLiteral(authorAffiliationValue, "en"));
            }
        }
        return creatorResource;
    }
    
    Resource createContactPoint (Model model, JsonArray contactPoints) {
        Resource contactPointResource = null;
        // just take the first one for now, if any
        
        if (!contactPoints.isEmpty()) {
            contactPointResource = model.createResource();
            
            JsonObject contactPointObj = contactPoints.getJsonObject(0);
            JsonObject contactName = contactPointObj.getJsonObject("datasetContactName");
            if (contactName != null) {
                String contactNameValue = contactName.getString("value", "");
                // But what if it is a ORCID or other autor identifier?
                contactPointResource.addProperty(
                        model.createProperty(VCARD, "fn"),
                        model.createLiteral(contactNameValue, "en"));
            }

            JsonObject contactEmail = contactPointObj.getJsonObject("datasetContactEmail");
            if (contactEmail != null) {
                String contactEmailValue = contactEmail.getString("value", "");
                if (!contactEmailValue.isEmpty()) {
                    contactPointResource.addProperty(
                            model.createProperty(VCARD, "hasEmail"),
                            "mailto:" + contactEmailValue);
                }
            }
            
            JsonObject contactAffiliation = contactPointObj.getJsonObject("datasetContactAffiliation");
            if (contactAffiliation != null) {
                String contactAffiliationValue = contactAffiliation.getString("value", "");
                if (!contactAffiliationValue.isEmpty()) {
                    // vcard for affiliation, supposed to be organization-name
                    contactPointResource.addProperty(
                            model.createProperty(VCARD, "organization-name"),
                            model.createLiteral(contactAffiliationValue, "en"));
                }
            }
        }
        
        return contactPointResource;
    }
    
    //--- JSON helper stuff, should be able to get better Dataverse JSON aware stuff from elsewhere?
    
    String getPrimitiveValueFromFieldsByTypeName(JsonArray fields, String typeName, String defaultValue) {
        for (int i = 0; i < fields.size(); i++) {
            JsonObject field = fields.getJsonObject(i);
            if (field.getString("typeName").equals(typeName)) {
                // if it has "typeClass": "primitive", just get the value
                if (field.getString("typeClass").equals("primitive") && 
                        !field.getBoolean("multiple", false)) {
                    return field.getString("value", defaultValue);
                }
            }
        }
        return defaultValue;
    }
    
    // we can have a primitive with multiple values as well
    List<String> getPrimitiveValueArrayFromFieldsByTypeName(JsonArray fields, String typeName)
    {
        for (int i = 0; i < fields.size(); i++) {
            JsonObject field = fields.getJsonObject(i);
            if (field.getString("typeName").equals(typeName)) {
                // if it has "typeClass": "primitive", just get the value
                if (field.getString("typeClass").equals("primitive")) {
                    // check that we have "multiple": true
                    if (!field.getBoolean("multiple", false)) {
                        // not multiple, list with single value
                        String singleValue = field.getString("value", "");
                        return java.util.Collections.singletonList(singleValue);
                    } else {
                        JsonArray valuesArray = field.getJsonArray("value");
                        // convert JsonArray to List<String>
                        List<String> valuesList = new java.util.ArrayList<>();
                        for (int j = 0; j < valuesArray.size(); j++) {
                            valuesList.add(valuesArray.getString(j, ""));
                        }
                        return valuesList;
                    }
                }
            }
        }
        return java.util.Collections.emptyList();
    }

    List<String> getValueArrayFromMultipleFieldByTypeName(JsonArray fields, String typeName) {
        for (int i = 0; i < fields.size(); i++) {
            JsonObject field = fields.getJsonObject(i);
            if (field.getString("typeName").equals(typeName)) {
                // if it has "typeClass": "multiple", get the value array
                if (field.getBoolean("multiple")) {
                    JsonArray valuesArray = field.getJsonArray("value");
                    // convert JsonArray to List<String>
                    List<String> valuesList = new java.util.ArrayList<>();
                    for (int j = 0; j < valuesArray.size(); j++) {
                        valuesList.add(valuesArray.getString(j, ""));
                    }
                    return valuesList;
                }
            }
        }
        return java.util.Collections.emptyList();
    }
    
    JsonArray getValuesFromCompoundFieldByTypeName(JsonArray fields, String typeName) {
        for (int i = 0; i < fields.size(); i++) {
            JsonObject field = fields.getJsonObject(i);
            if (field.getString("typeName").equals(typeName)) {
                // if it has "typeClass": "compound", get the value array
                if (field.getString("typeClass").equals("compound")) {
                    return field.getJsonArray("value");
                }
            }
        }
        // return an empty JsonArray
        return Json.createArrayBuilder().build();
    }
}
