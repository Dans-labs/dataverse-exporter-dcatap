DCAT-AP Exporter for Dataverse
==============================

This exporter is based on the [DCAT-AP](https://semiceu.github.io/DCAT-AP/releases/3.0.0/) 3.0.0 specification for describing datasets in a standardized way. 
It allows users to export metadata from Dataverse Datasets into a DCAT-AP compliant format.

> [!WARNING]
> This exporter is a work in progress and may not yet fully comply with the DCAT-AP specification.

Installation
------------
After building the jar with `mvn clean install`, 
you can copy this into a directory on the server where Dataverse can load it. 
Dataverse must be configured to load extra exporters from this directory. 
See the Dataverse documentation for more details:
https://guides.dataverse.org/en/latest/installation/config.html#dataverse-spi-exporters-directory


# Testing

## Exporter Output

In order to test compliance of the exporter's output with the DCAT-AP specification, the recommended approach is to validate exporter's output against the DCAT-AP SHACL shape [dcat-ap-SHACL.ttl](https://github.com/SEMICeu/DCAT-AP/blob/master/releases/3.0.0/shacl/dcat-ap-SHACL.ttl).

Compliance testing is performed against example output file [src/test/resources/cars/expected/cars.ttl](src/test/resources/cars/expected/cars.ttl), which is validated through the script [src/test/test-output.sh](src/test/test-output.sh). Testing results are stored in (non-tracked) files:
* `src/test/resources/cars/validation_output.ttl` containing the full validation report
* `src/test/resources/cars/shacl-results.csv` containing the validation report

**Requirements**:
* APACHE Jena [SHACL validator](https://jena.apache.org/documentation/shacl/)
* APACHE Jena [ARQ SPARQL processor](https://jena.apache.org/documentation/query/)