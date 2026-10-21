# CountryQuest offline catalog — source notices

Prepared: 2026-10-21. These notices describe this draft dataset, not all third-party software in CountryQuest.

## Country data

Source: countries.dev, https://countries.dev/countries (snapshot retained in source/countries.raw.json).
Provider documentation: https://countries.dev/docs
Provider attribution: https://countries.dev/ identifies underlying geographic data as GeoNames, CC BY 4.0.
GeoNames: https://www.geonames.org/
License link: https://creativecommons.org/licenses/by/4.0/

CountryQuest stores a subset of fields, trims text, normalizes Unicode to NFC, sorts by country code and removes repeated language labels. The four factual edits and their primary sources are recorded in review/corrections.json. The original response is retained for comparison. Seed version is app-owned and does not imply a provider dataset version. No endorsement by the providers is implied.

## Flag images

249 bundled SVGs are copied without modification from the Flagpedia/FlagCDN SVG archive:
https://flagcdn.com/svg.zip
https://flagpedia.net/download/vector
https://flagpedia.net/download

Flagpedia describes its downloadable images as public domain and available for commercial and non-commercial use, based on Wikimedia Commons vectors. Filenames are mapped by country code. We acknowledge Flagpedia.net as the source. The archive's Afghanistan SVG is not included in the bundled assets; see below.

## Afghanistan SVG override

Asset: assets/flags/af.svg
Source: https://upload.wikimedia.org/wikipedia/commons/5/5c/Flag_of_the_Taliban.svg
File description/license: https://commons.wikimedia.org/wiki/File:Flag_of_the_Taliban.svg
Vector author credited by the file page: Lexicon.
The file page describes the SVG as public domain. The file is copied unchanged and matches the variant linked by the configured country API when this snapshot was prepared. Source and checksum are recorded in the manifest.

## Review boundaries

A provider's copyright/license statement does not settle geographic ambiguity or which flag variant a quiz should use. Additional source review and Android decoding tests are recorded as pending in README.md. These notices must accompany the distributed dataset and relevant changes must remain documented.
