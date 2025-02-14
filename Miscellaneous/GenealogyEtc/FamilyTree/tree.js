/******************************************************************************/
/* Licensing
   =========

   This file has been produced by the STEPBible project, and is made available
   under the CC BY 4.0 licence.

   You do not need permission to use the code or the data upon which it relies,
   but we would love to hear about anything you do with it.

   We would be grateful if you could inform us of any errors you discover --
   please email us at Feedback @ gmail.com.

$$$ Need to add details of the people.json file and also perhaps the
    location where this file can be found.




   Functionality
   =============

   - This displays a family tree rooted at a chosen Bible individual.

   - The information comprises a text box giving details of the person and
     their relatives; and a graphical display area which can be configured to
     show either a descendant chain or an ancestor chain.

   - To show a full tree can be rather challenging, given the amount of material
     which may need to be displayed.  Facilities are provided to limit the
     display either to an approximate number of people, or to a number of
     generations.  (These are configurable, but at present only by changing
     the code.  Details below.)  Where the tree is truncated at an individual
     who has children (so that the children are not displayed), the individual's
     name is followed by an ellipsis.  Ditto if showing an ancestor tree and the
     tree is truncated an at individual who has parents who are not shown.

   - There is a node for each individual, represented by a small circle.
     Clicking on that circle or the associated label brings up a window showing
     details of the individual.

   - The display can be scrolled left or right or up or down, and zoomed using
     the mouse wheel.  (At the time of writing you can also control the
     spacings between nodes.  This level of configurability may perhaps be a
     distraction for the user, so we may need to revisit how much control is
     actually needed.)

   - If you click on a different individual amongst those currently displayed,
     the text box is updated to contain details of that individual, but the tree
     is not altered.

   - If you click on a link in the text box, the tree is redrawn rooted at that
     individual, and the text box is updated.

   - You can also select an individual from a drop-down.

   - When you select an individual, the node for that individual is
     highlighted.  When showing an ancestor tree, it is possible that the
     same individual may appear more than once, for example because they may
     be an ancestor of both the mother and father of the individual.  In
     such a case, all instances of that particular individual are highlighted.
     (Note that more than one person may have the same name.  Only occurrences
     of the selected individual are highlighted, not other people with the
     name name.)





   API
   ===

   - The code below starts with a configuration section which lets you specify
     default layout settings.

   - renderTreeForPerson draws the tree for a given individual.  The argument
     should be a fully qualified name -- eg 'Aaron@Exo.4.14-Heb'.

   - renderTreeForStrongs draws the tree for a given Strongs number (which
     equates to a given individual).

   - The code assumes a set-up like that supplied by index.html, and also
     assumes that my standard people.json file is available.  (Currently
     the records in that file contain quite a number of fields which are
     not used here, so the size of the file can probably be reduced quite
     significantly.)





   Evaluation harness
   ==================

   At present the code is set up mainly as a proof-of-concept.  To get
   round limitations upon accessing files from a web page, for instance,
   it requires you to select the input file manually (despite the fact that
   it is always the same file); and it supplies hooks so that you can alter
   the layout dynamically so as to find out what looks best.

   There are a number of issues to be resolved before this can be used in
   earnest:

   - On entry at present, the tree is drawn for the first person in the
     drop-down.  We may not wish to continue with the drop-down at all (it
     contains an awful lot of entries).  The intention is that the facilities
     should be accessible from the sidebar in much the same way as, say,
     maps can be accessed.  There is also a proposal to have a web page to
     give a more sophisticated means of accessing entries directly than can
     be handled by a simple drop-down.

   - Does it do all the things we might want, in a way which we are happy with
     -- and nothing we do _not_ want?

   - To date I have been trying this only on a desktop machine with a mouse
     and a large screen.  I am not presently sure how this would translate to
     a smaller screen or a touch screen.

   - This needs to be interfaced properly with STEPBible.  That definitely
     means changing things so that the data is picked up automatically rather
     than via a file chooser.  It probably also means coming up with a better
     way of determining the layout parameters.  And it also requires us to
     find a way of passing to the code here details of the person whose
     details are to be displayed.

   - The people.json file is presently just a copy of my full people file.
     In fact it contains a number of fields which the processing here does
     not use, and which could therefore be removed -- probably desirable,
     because teh file is large.





   Implementation notes
   ====================

   The full data is held in GenealogyData.  This is a map keyed on the
   full names of people (including their scripture references, for
   disambiugation purposes).  Each name is associated with a record which
   holds most of the information taken from the people.json file, with
   some fields slightly modified.

   Each name also has an integer index value associated with it.  This makes it
   possible to refer to people easily, without having to have recourse to full
   names everywhere.  There is no particular significance to the actual index
   values -- I merely need something unique.  In fact, they are the indexes
   into the QualifiedNamesList array.  The nth entry in that is the full name
   of the n'th individual.

   A separate map -- StrongsMap -- relates each dStrongs value to the index
   associated with the individual to whom that Strongs number applies.

   Each node in the tree has an ix member which gives the index for the
   individual represented by that node, thus bringing the two structures
   together.






   Acknowledgements
   ================

   With grateful thanks to ChatGPT ...


   'Jamie' Jamieson   STEPBible   Jan 2025
*/

/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                   Configuration -- change as required                    **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
/* This controls the manner in which the famiy trees are truncated on screen (if
   at all).  Set TreeTruncater to one of:

   - truncateTreeToNumberOfPeople: Displays approximately that number of people
     in the tree, but balances things, so that all branches of the tree are
     truncated at the same level.  The number of people to be shown is
     determined by MaxNumberOfIndividualsToShow.

   - truncateTreeToNumberOfGenerations: Shows just the given number of
     generations.  The number of genertions to show is determined by
     MaxNumberOfIndividualsToShow.

   - truncateTreeDont: The tree is not truncated at all.  You can still scroll,
     stretch, control node spacings etc -- although on a large tree it may be
     difficult to come up with settings that make it usable. */

const MaxNumberOfGenerationsToShow = 5;
const MaxNumberOfIndividualsToShow = 50;
const TreeTruncater = truncateTreeToNumberOfPeople; // or truncateTreeToNumberOfGenerations or truncateTreeDont.

      
/******************************************************************************/
/* Change this to alter the default node spacings etc. */

function getLayoutHandler ()
{
    if (null === LayoutHandler) LayoutHandler = new VerticalLayoutHandler ( { spacingBetweenLayers: 200, spacingBetweenSiblings: 50, fontSizeForNames: "small" } );
    return LayoutHandler;
}

/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                           End of configuration                           **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/





/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                                   API                                    **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
/* Draws whatever kind of tree is currently selected (descedant tree or
   ancestor tree), based upon the given name.

   nameKey: Fully qualified name -- eg 'Aaron@Exo.4.14-Heb'.
*/

function renderTreeForPerson (nameKey)
{
    renderTreeForPersonA(nameKey)
}


/******************************************************************************/
/* Draws whatever kind of tree is currently selected (descedant tree or
   ancestor tree), based upon the given Strong's value.

   strongs: Strongs number -- eg 'G1234'.
*/

function renderTreeForStrongs (strongs)
{
    renderTreeForPersonA(lookupStrongs(strongs))
}

/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                               End of API                                 **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/





/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                       HTML interface and globals                         **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
let CurrentPersonNameKey = null;                  // The current person.
let InstancesOfEachPersonInCurrentDisplay = null; // Used to make it possible to highlight multiple instances of the same person.
let GenealogyData = {};                           // The fundamental data, slightly amended and extended.
let LayoutHandler = null;                         // Controls node-placement etc.
let QualifiedNamesList = [];                      // Converts indices to names-with-scripture-references.
let StrongsMap = new Map();                       // Enables lookup based on dStrongs.
let TruncateIndices = new Set();                  // Index values of elements at which the tree has been truncated to keep it within reasonable bounds.

/******************************************************************************/
const svg = d3.select("svg");
const g = svg.append("g");

const zoom = d3.zoom()
   .scaleExtent([0.5, 2]) // Set zoom range
   .on("zoom", (event) => g.attr("transform", event.transform));
svg.call(zoom);


/******************************************************************************/
/* Arranges to handle the data file when it is selected. */

document.getElementById("fileInput").addEventListener("change", handleFile);


/******************************************************************************/
/* Handles the radio buttons which determine whether we are showing descentants
   or ancestors. */

let ViewMode = "descendants"; // Default view mode
const ViewModeButtons = document.querySelectorAll('input[name="viewMode"]');
ViewModeButtons.forEach(radio => {
    radio.addEventListener("change", (event) => {
        ViewMode = event.target.value;
        renderTreeForPersonA(CurrentPersonNameKey); // Redraw the tree based on the mode
    });
});


/******************************************************************************/
/* Handles the radio buttons which determine display orientation. */

const OrientationButtons = document.querySelectorAll('input[name="orientation"]');
OrientationButtons.forEach(radio => {
    radio.addEventListener("change", (event) => {
        Orientation = event.target.value;
        renderTreeForPersonA(CurrentPersonNameKey); // Redraw the tree based on the mode
    });
});


/******************************************************************************/
/* Handles the sliders which determine spacing. */

const SpacingBetweenSiblingsSlider = document.getElementById("spacingBetweenSiblingsSlider");
const SpacingBetweenSiblingsSliderValue = document.getElementById("spacingBetweenSiblingsSliderValue");

SpacingBetweenSiblingsSlider.addEventListener("input", (event) => {
    n = parseInt(event.target.value, 10);
    SpacingBetweenSiblingsSliderValue.textContent = n;
    getLayoutHandler().spacingBetweenSiblings = n
    renderTreeForPersonA(CurrentPersonNameKey);
});


const SpacingBetweenGenerationsSlider = document.getElementById("spacingBetweenGenerationsSlider");
const SpacingBetweenGenerationsSliderValue = document.getElementById("spacingBetweenGenerationsSliderValue");

SpacingBetweenGenerationsSlider.addEventListener("input", (event) => {
    n = parseInt(event.target.value, 10);
    SpacingBetweenGenerationsSliderValue.textContent = n;
    getLayoutHandler().spacingBetweenLayers = n
    renderTreeForPersonA(CurrentPersonNameKey);
});

/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                    End of HTML interface and globals                     **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/





/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                             Initialisation                               **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
/* Data storage and indexing -- see implementation notes at head of file for
   more information. */

function buildIndexes ()
{
    for (const nameKey in GenealogyData) {
	const person = GenealogyData[nameKey];
	const ix = QualifiedNamesList.length;
	person.ix = ix;
        person.alternativeNames = null == person.summaryOfSupplementaryData.allNames ? null : makeAlternativeNames(person.summaryOfSupplementaryData.allNames);
        person.summaryDescription = person.summaryDescription.split(",")[0].substring(1) + ".";
	person.longDescription = person.longDescription.replaceAll("¶", "<p>");
	person.summaryIcon = makeSummaryIcon(person);
	person.genderOrGroupIndicator = makeGenderOrGroupIndicator(person);
	QualifiedNamesList.push(nameKey);
	person.summaryOfSupplementaryData.allDStrongs.forEach((entry) => { StrongsMap.set(entry, ix) });
    }

    markJesusTree("Jesus@Isa.7.14-Rev");
}

    
/******************************************************************************/
/* Gets the full name of a person, including the scripture reference portion
   used for disambiguation. */

function getPersonNameWithReferencesFromIndexes (ix)
{
    return QualifiedNamesList[ix]
}


/******************************************************************************/
/* Gets the data record describing a person given their index. */

function getPersonRecordFromIndexes (ix)
{
    return GenealogyData[QualifiedNamesList[ix]]
}


/******************************************************************************/
/* Loads data from file.  This is needed only for this sample application; if
   something akin to this is ever put live, we'd need to pick the data up by
   a different means. */

function handleFile (event)
{
    const file = event.target.files[0];
    const reader = new FileReader();
    reader.onload = function (e) {
	GenealogyData = JSON.parse(e.target.result);
        initialise()
    };
    reader.readAsText(file);
}


/******************************************************************************/
function initialise ()
{
    buildIndexes();
    populateDropdown();

    svg.append("defs").append("marker")
      .attr("id", "arrowhead-start")
      .attr("viewBox", "0 -5 10 10")
      .attr("refX", 0)
      .attr("refY", 0)
      .attr("markerWidth", 6)
      .attr("markerHeight", 6)
      .attr("orient", "auto")
      .append("path")
      .attr("d", "M 10,-5 L 0,0 L 10,5") // Pointing backward
      .attr("class", "arrowhead");

    svg.append("defs").append("marker")
      .attr("id", "arrowhead-end")
      .attr("viewBox", "0 -5 10 10")
      .attr("refX", 10)
      .attr("refY", 0)
      .attr("markerWidth", 6)
      .attr("markerHeight", 6)
      .attr("orient", "auto")
      .attr("fill", 255)
      .append("path")
      .attr("d", "M 0,-5 L 10,0 L 0,5")
      .attr("class", "arrowhead");
}


/******************************************************************************/
function markJesusTree (nameKey)
{
    if ("-" == nameKey) return
    let personRecord = GenealogyData[nameKey];
    personRecord.jesusTree = true;
    markJesusTree(personRecord.father.augmentedUnifiedName);
    markJesusTree(personRecord.mother.augmentedUnifiedName);
}


/******************************************************************************/
/* Populates the dropdown used to select individuals, and arranges to display
   the first person on the dropdown.  Probably wanted in this form only for
   evaluation purposes. */

function populateDropdown ()
{
    const personSelect = document.getElementById("personSelect");
    personSelect.innerHTML = ""; // Clear previous options
    Object.keys(GenealogyData).forEach(person => {
        const option = document.createElement("option");
        option.value = person;
        option.textContent = person;
        personSelect.appendChild(option);
    });

    personSelect.addEventListener("change", () => {
        CurrentPersonNameKey = personSelect.value;
        renderTreeForPersonA(CurrentPersonNameKey);
    });



    // This displays the first person in the drop-down once the drop-down has
    // been populated.  We will presumably want to do something different in
    // future.
    
    personSelect.value = Object.keys(GenealogyData)[0];
    CurrentPersonNameKey = personSelect.value;//"Adam@Gen.2.19-Jud"
    renderTreeForPersonA(CurrentPersonNameKey);
}


/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                         End of initialisation                            **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/





/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                                Names                                     **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
/* The JSON data which represents the genealogy is keyed on what I refer to
   here as nameKeys.  These comprise the name of the individual, followed by
   an @-sign and then scripture reference information.  This format is needed
   in order to access the genealogy data.

   Then there are various forms of displayed name.  All of these are based
   upon what I refer to here as the baseName, which is the portion of the
   nameKey before the @-sign (ie it's the actual name of the individual,
   devoid of any scripture reference information).

   This information is augmented with various additional indicators --
   perhaps an emoji at the start of the name to indicate the person's role,
   or an icon at the end of the name to indicate sex, or an ellipsis to
   reflect the fact that the person has offspring who do not presently
   appear on the screen (trees may be truncated to avoid cluttering things too
   much).

   Each of these various forms is handled here by a separate function, so
   that it is possible to alter each form of presentation independently.
*/

/******************************************************************************/
/* Takes a name possibly including the scripture reference appended to names
   for disambiguation purposes, and returns the basic name devoid of scripture
   details.  Used for display purposes. */

function baseName (nameKey)
{
    return nameKey.split("@")[0]
}


/******************************************************************************/
/* Extracts the first scripture reference from a nameKey. */

function getFirstScriptureReference (personRecord)
{
    return personRecord.referenceFromUnifiedName.split("-")[0]
}


/******************************************************************************/
/* Converts integer to single-character string.  Intended mainly to handle
   3-byte Unicode characters. */

function u (charCode)
{
    return String.fromCodePoint(charCode)
}


/******************************************************************************/
/* Makes an indicator appended to the end of a name to indicate the sex of the
   person or whether the name in fact refers to a group.  PseudoEntry below
   refers to entries I have added to the people.json file while generating it,
   and represent, for instance elements which some people believe to be people,
   and others believe to be towns. */

const GenderOrGroupIndicators = [
    ["Male", ""],
    ["Female", " " + u(0x2640) ],
    ["Group", " " + u(0x2642) + u(0x2642) + u(0x2640) + u(0x2640) ],
    ["PseudoEntry", ""]
];

function makeGenderOrGroupIndicator (personRecord)
{
    for (const [key, text] of GenderOrGroupIndicators)
	if (personRecord.type == key)
            return text

    return ""
}    


/******************************************************************************/
/* Makes an icon displayed at the front of names to indicate the role of the
   individual. */

const SummaryIconDetails = [
    [ /\W+a\s+man\W+/i,          "" ],
    [ /\W+group\W+/i,            u(0x1F465) ],               // Two profile heads as a single symbol.
    [ /\W+people\W+/i,           u(0x1F465) ],               // Two profile heads as a single symbol.
    [ /\W+emperor\W+/i,          u(0x1F451) ],               // Crown.
    [ /\W+high\s+priest\W+/i,    u(0x265d) ],                // Chess bishop.
    [ /\W+judge\w+/i,            u(0x2692) ],                // Scales.
    [ /\W+king\W+/i,             u(0x1F451) ],               // Crown.
    [ /\W+prophet\W+/i,          u(0x1F54A) ],               // Dove.
    [ /\W+official\W+/i,         u(0x270D) ],                // Hand, writing.
    [ /\W+priest\W+/i,           u(0x1F64F) ],               // One pair of praying hands.
    [ /\W+prince\W+/i,           u(0x1F451) ],               // One crown.
    [ /\W+singer\W+/i,           u(0x1F3A4) + u(0x1F3B5) ],  // Microphone plus musical notes.
    [ /\W+apostle\W+/i,          u(0x1F5E3) + u(0x1F30D) ],  // Speaking head, and globe.
    [ /\W+queen\W+/i,            u(0x1F451) ],               // Two crowns.
    [ /\W+governor\W+/i,         u(0x1F3DB) ],               // Classical building.
    [ /\W+ruler\W+/i,            u(0x1F3DB) ],               // Classical building.
    [ /\W+ethnarch\W+/i,         u(0x1F3DB) ],               // Classical building.
    [ /\W+tetrarch\W+/i,         u(0x1F3DB) ],               // Classical building.
    [ /\W+Egyptian pharaoh\W+/i, u(0x1F451) + u(0x1F42B) ],  // Crown and camel.
    [ /\W+ancestors\W+/i,        u(0x1F465) ],               // Two profile heads as a single symbol.
];

function makeSummaryIcon (personRecord)
{
  for (const [regex, text] of SummaryIconDetails)
    if (regex.test(personRecord.summaryDescription))
        return text

    return ""
}


/******************************************************************************/
/* Takes a name which may include the scripture references used for
   disambiguation purposes and returns a version of that name suitable for
   display in eg the siblings list in the info box (presently, in fact,
   just the base name). */

function nameForDisplayInBodyOfInfoBoxGivenNameKey (nameKey)
{
    return GenealogyData[nameKey].baseNameFromUnifiedName;
}


/******************************************************************************/
/* Takes a person record and returns a version of that name suitable for
   display in eg the siblings list in the info box (presently, in fact,
   just the base name). */

function nameForDisplayInBodyOfInfoBoxGivenPersonRecord (personRecord)
{
    return personRecord.baseNameFromUnifiedName;
}


/******************************************************************************/
/* In the tree we display an icon describing the role of the person, the base
   name, a gender / group indicator (where not male) and an ellipsis if the
   tree has been truncated at this point. */

function nameForDisplayInTree (ix)
{
    const personRecord = getPersonRecordFromIndexes(ix);
    return personRecord.summaryIcon +
	personRecord.baseNameFromUnifiedName +
	personRecord.genderOrGroupIndicator +
	(TruncateIndices.has(ix) ? "..." : "");
}





/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                        General implementation                            **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
/* Builds up the relationship data structure, but not the graphical
   representation.

   Note that the link field here apparently has to be called 'children' even
   though what we are actually dealing with is the parents -- the name
   'children' appears to be meaningful to the underlying library facilities. */

function buildAncestorHierarchy (nameKey)
{
    const person = GenealogyData[nameKey];
    if (!person) return null;

    parents = [person.father, person.mother].filter(x => x.augmentedUnifiedName !== "-")

    return {
	ix: person.ix,
        children: parents.map(parent => buildAncestorHierarchy(parent.augmentedUnifiedName))
    };
}


/******************************************************************************/
/* Builds up the relationship data structure, but not the graphical
   representation. */

function buildDescendantHierarchy (nameKey)
{
    const person = GenealogyData[nameKey];
    if (!person)
      return null;

    return {
	ix: person.ix,
        children: person.offspring.map(child => buildDescendantHierarchy(child.augmentedUnifiedName))
    };
}


/******************************************************************************/
/* Fills in the content of the info box, sorts out links, etc. */

function fillInfoBox (ix)
{
  /****************************************************************************/
  let personRecord = getPersonRecordFromIndexes(ix)
  highlightNodeAndDuplicates(ix);
    


  /****************************************************************************/
  const personName = personRecord.baseNameFromUnifiedName + "@" + personRecord.referenceFromUnifiedName;
  const fatherName = personRecord.father.augmentedUnifiedName;
  const motherName = personRecord.mother.augmentedUnifiedName;
  const partners   = personRecord.partners || [];
  const offspring  = personRecord.offspring || [];
  const siblings   = personRecord.siblings || [];
  const summaryDescription = personRecord.summaryDescription;
  const summaryIcon = personRecord.summaryIcon;
  const longDescription = personRecord.longDescription;
  const alternativeNames = null == personRecord.alternativeNames ? "" : "aka " + personRecord.alternativeNames + " ";

  let ambiguity = "-" == personRecord.ambiguity ? "" :
      "<p><b>Differing interpretations exist here.</b>  Click <a href='" + personRecord.ambiguity + "' target='_blank'>here</a> for details.</p>";

  const partnerList = 0 == partners.length ? "" :
    `<p><b>Partners:</b>  ${partners.length  > 0 ? partners.map (partner => `<span class="partner-link simulated-link">${nameForDisplayInBodyOfInfoBoxGivenNameKey(partner.augmentedUnifiedName)}</span>`).join(", ") : "-" }</p>`;

  const siblingList = 0 == siblings.length ? "" :
    `<p><b>Siblings:</b>  ${siblings.length  > 0 ? siblings.map (sibling => `<span class="sibling-link simulated-link">${nameForDisplayInBodyOfInfoBoxGivenNameKey(sibling.augmentedUnifiedName)}</span>`).join(", ") : "-" }</p>`;
    
  const offspringList = -1 == siblings.length ? "" :
    `<p><b>Offspring:</b> ${offspring.length > 0 ? offspring.map(kid     => `<span class="offspring-link simulated-link">${nameForDisplayInBodyOfInfoBoxGivenNameKey(kid.augmentedUnifiedName)}</span>`).join(", ") : "-" }</p>`;

  const summaryDescriptionX = "" == summaryDescription.replace("-", "") ? "" : "<p>" + summaryDescription + "</p>";

  const longDescriptionX    = "" == longDescription.replace   ("-", "") ? "" : "<p>" + longDescription    + "</p>";
    
  const summaryIconX = "" == summaryIcon ? "" : `<span style='font-size:x-large'>${summaryIcon}</span>`

  const infoBoxContent = d3.select("#info-box-content")
  infoBoxContent  
      .html(`
            <p>${makeSummaryIcon(personRecord)}
              <span class="person-link simulated-link">${nameForDisplayInBodyOfInfoBoxGivenPersonRecord(personRecord)}</span>
              ${alternativeNames}(first mentioned at ${getFirstScriptureReference(personRecord)})
            </p>

            ${partnerList}

            <p><b>Father:</b> ${fatherName !== "-" ? `<span class="father-link simulated-link">${nameForDisplayInBodyOfInfoBoxGivenNameKey(fatherName)}</span>` : "Unknown"}
            &nbsp;&nbsp;&nbsp;<b>Mother:</b> ${motherName !== "-" ? `<span class="mother-link simulated-link">${nameForDisplayInBodyOfInfoBoxGivenNameKey(motherName)}</span>` : "Unknown"}
            ${siblingList}
            ${offspringList}
            ${ambiguity}
            ${summaryDescriptionX}
            ${longDescriptionX}
      `);

    infoBoxContent.select(".person-link").on("click", () => { renderTreeForPersonA(personName); });
    if (fatherName !== "-") { infoBoxContent.select(".father-link").on("click", () => { renderTreeForPersonA(fatherName); }); }
    if (motherName !== "-") { infoBoxContent.select(".mother-link").on("click", () => { renderTreeForPersonA(motherName); }); }
    siblings .forEach((sibling, index) => { infoBoxContent.selectAll(".sibling-link")  .filter((d, i) => i === index).on("click", () => { renderTreeForPersonA(sibling.augmentedUnifiedName); }); });
    partners .forEach((partner, index) => { infoBoxContent.selectAll(".partner-link")  .filter((d, i) => i === index).on("click", () => { renderTreeForPersonA(partner.augmentedUnifiedName); }); });
    offspring.forEach((kid,     index) => { infoBoxContent.selectAll(".offspring-link").filter((d, i) => i === index).on("click", () => { renderTreeForPersonA(kid    .augmentedUnifiedName); }); });

    document.getElementById("info-box").scrollTop = 0;

    CurrentPersonNameKey = personSelect.value = personName;
}


/******************************************************************************/
/* Identifies instances of all people in the tree as currently displayed, and
   in particular finds duplicates. */

function findInstancesOfEachPersonInCurrentDisplay (rootNodeOfTreeBeingDisplayed)
{
    const duplicates = new Map();
    
    rootNodeOfTreeBeingDisplayed.each(node => {
      if (duplicates.has(node.data.name))
        duplicates.get(node.data.name).push(node); // Add to the list of duplicate nodes
      else
        duplicates.set(node.data.name, [node]); // Initialize with the first occurrence
    });

    return duplicates
}


/******************************************************************************/
/* Arranges that a selected individual and any other instances of that same
   person in the tree are highlighted. */

function highlightNodeAndDuplicates (ix)
{
    unhighlightAllNodes()
    d3.selectAll("circle").style("fill", d => d.data.ix == ix ? "red" : "steelblue"); // Change duplicate color to red
}


/******************************************************************************/
/* Returns the full name (including scripture reference) for the individual
   associated with a given Strongs number. */

function lookupStrongs (strongs)
{
    let ix = StrongsMap.get(strongs)
    return QualifiedNamesList[ix]
}

    
/******************************************************************************/
/* Extracts alternative names for an individual from the supplementary allNames
   field. */

const C_AlternativeNamesRegex = /\b(or)\b/i;
function makeAlternativeNames (details)
{
    const match = C_AlternativeNamesRegex.exec(details);
    return match ? details.substring(match.index + 2).trim() : null;
}


/******************************************************************************/
/* Called on clicking a node or the associated text label.  Fills the info
   box. */

function nodeOnClick (event, d)
{
    fillInfoBox(d.data.ix);
}


/******************************************************************************/
/* Draws the tree and creates click handlers etc. */

function renderTreeForPersonA (nameKey, depth = 0)
{
  /****************************************************************************/
  unhighlightAllNodes()


    
  /****************************************************************************/
  const rootNodeOfSvgTree = "descendants" == ViewMode ? buildDescendantHierarchy(nameKey) : buildAncestorHierarchy(nameKey, GenealogyData);
  const layoutHandler = getLayoutHandler()
  const treeLayout = d3.tree()
    .nodeSize([layoutHandler.spacingBetweenSiblings, layoutHandler.spacingBetweenLayers])
    .separation((a, b) => (a.parent === b.parent ? 1 : 1.5)); // More spacing between unrelated nodes
  TreeTruncater(rootNodeOfSvgTree);
  const treeData = treeLayout(d3.hierarchy(rootNodeOfSvgTree));



  /****************************************************************************/
  g.selectAll("*").remove(); // Clear previous tree



  /****************************************************************************/
  /* Draw tree. */

  const nodes = layoutHandler.renderLinksAndNodes(treeData)
  InstancesOfEachPersonInCurrentDisplay = findInstancesOfEachPersonInCurrentDisplay(nodes)
  nodes.append("circle").attr("r", 5)
  layoutHandler.positionText(nodes);
  fillInfoBox(rootNodeOfSvgTree.ix);



  /****************************************************************************/
  /* Add functionality to deal with node-clicks. */
    
  nodes.on("click", nodeOnClick);
  nodes.style("cursor", "pointer");
  textNodes = svg.selectAll("text");
  textNodes.style("cursor", "pointer");
  textNodes.style("font-size", layoutHandler.fontSizeForNames);
  layoutHandler.adjustPositionOfRootNode(textNodes, rootNodeOfSvgTree.ix);
}


/******************************************************************************/
/* A tree truncater which doesn't actually do any truncation. */

function truncateTreeDont (rootSvgNode, depth = 0)
{
    return;
}


/******************************************************************************/
/* Lets you truncate the tree at a certain number of generations below the root
   so as to keep the tree from growing excessively. */

function truncateTreeToNumberOfGenerations (rootSvgNode, depth = MaxNumberOfGenerationsToShow, first = true)
{
    if (depth <= 0 || 9999 == depth)
	return;
    
    if (first)
	TruncateIndices.clear();
	
    if (depth == 1)
    {
        if (rootSvgNode.children && rootSvgNode.children.length > 0)
	    TruncateIndices.add(rootSvgNode.ix)
	
        rootSvgNode.children = null;
    }
    else if (rootSvgNode.children)
        rootSvgNode.children.forEach(child => truncateTreeToNumberOfGenerations(child, depth - 1, false));
}


/******************************************************************************/
/* Lets you truncate the tree at a certain level below the root so as to keep
   the tree from growing excessively.  In this case you specify the approximate
   number of nodes the tree should show.  It looks as though it does then take
   notice of this -- more or less.  I've been struggling with the code here, and
   am not sure I've actually nailed it. */

var DepthAtWhichToTruncate = 9999;
function truncateTreeToNumberOfPeople (rootSvgNode, maxPeople = MaxNumberOfIndividualsToShow)
{
    if (maxPeople <= 0)
	return;

    DepthAtWhichToTruncate = 9999;
    
    truncateTreeToNumberOfPeopleA(rootSvgNode, maxPeople, 0);
    truncateTreeToNumberOfGenerations(rootSvgNode, DepthAtWhichToTruncate);
}


/******************************************************************************/
/* Support function truncateTreeToNumberOfPeople. */

function truncateTreeToNumberOfPeopleA (rootSvgNode, peopleToGo, depth)
{
    if (!rootSvgNode.children)
	return;

    const n = 1 + rootSvgNode.children.length; // 1 for the present node, and then an extra count for the children.
    if (n >= peopleToGo) // If that would take us past the limit, then we definitely have to truncate at 'depth', if not before.
    {
	DepthAtWhichToTruncate = Math.min(DepthAtWhichToTruncate, depth + 2);
	return;
    }

    rootSvgNode.children.forEach(child => {
	truncateTreeToNumberOfPeopleA(child, peopleToGo - n, depth + 1);
    })
}


/******************************************************************************/
/* Turns off the red highlightin markers. */

function unhighlightAllNodes ()
{
  d3.selectAll("circle").style("fill", "steelblue"); // Set the default color
}





/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
/* There used to be a HorizontalLayoutHandler too, which is why this has been
   hived off as a separate class. */

class VerticalLayoutHandler
{
  /****************************************************************************/
  constructor ( {spacingBetweenLayers = 0, spacingBetweenSiblings = 0, fontSizeForNames = "small" } = {})
  {
      this.spacingBetweenLayers = spacingBetweenLayers;
      this.spacingBetweenSiblings = spacingBetweenSiblings;
      this.fontSizeForNames = fontSizeForNames;
  }

    
  /****************************************************************************/
  /* Positions the root node so that the tree sits reasonably well within the
     available space. */

  adjustPositionOfRootNode (textNodes, rootNodeIx)
  {
    const nameAsDisplayedInTree = nameForDisplayInTree(rootNodeIx)
    textNodes
      .each(function () {
	  if (this.textContent == nameAsDisplayedInTree)
	  {
	      const svgWidth = svg.node().getBoundingClientRect().width; // Width of SVG container.
	      const newY = 20
	      const newX = svgWidth / 2
              const transform = d3.zoomIdentity.translate(newX, newY).scale(1);
              svg.call(zoom.transform, transform);	    
              return false
	  }
      });
  }


  /****************************************************************************/
  /* Renders the text, and positions it so as to avoid clashes as far as
     possible. */

  positionText (nodes)
  {
      nodes.append("text")
	  .attr("x", +10) // Adjust position based on parent/child
	  .attr("dy", 3) // Vertical offset
	  .attr("text-anchor", "start") // Align text properly
	  .attr("transform", "rotate(30)") // Apply rotation
  	  .text(d => nameForDisplayInTree(d.data.ix));
  }



  /****************************************************************************/
  /* Positions the root node so that the tree sits reasonably well within the
     available space. */

  renderLinksAndNodes (treeData)
  {
    // 29-Jan-25: Add pop-up on links.
    if (!this.LinkToolTip)
	this.LinkToolTip = d3.select("#linkToolTip");

    // Render links
    if ("descendants" == ViewMode)
    {
      g.selectAll(".link")
        .data(treeData.links())
        .join("path")
        .attr("class", "link")
        .attr("marker-end", "url(#arrowhead-end)")
        .attr("d", d3.linkVertical()
              .x(d => d.x)
              .y(d => d.y))
        .style("stroke", d => getPersonRecordFromIndexes(d.source.data.ix).jesusTree && getPersonRecordFromIndexes(d.target.data.ix).jesusTree ? "red" : "#ccc")

	// 29-Jan-25: Add pop-up on links.
        .on("mouseover", (event, d) => {
	    let person = getPersonRecordFromIndexes(d.target.data.ix);
	    let father = "-" == person.father.augmentedUnifiedName ? "Unknown" : baseName(person.father.augmentedUnifiedName);
	    let mother = "-" == person.mother.augmentedUnifiedName ? "Unknown" : baseName(person.mother.augmentedUnifiedName);

            this.LinkToolTip // Need to fill in the pop-up so we know what its width is.
		.style("display", "block")
                .html(`<b>${person.baseNameFromUnifiedName}</b><br>Father: ${father}.<br>Mother: ${mother}.`);

	    const tooltipWidth = this.LinkToolTip.node().offsetWidth;
	    const svgRightEdge = document.querySelector("svg").getBoundingClientRect().right;
	    let leftPosition = event.pageX; // Assume we can put the tooltip to the right of the mouse position until we know otherwise.

	    if (leftPosition + tooltipWidth > svgRightEdge) // If that would overflow the right margin, move the position,
		leftPosition = event.pageX - tooltipWidth/2;

            this.LinkToolTip
                .style("left", (leftPosition + 5) + "px")
                .style("top", (event.pageY + 10) + "px");
        })
        .on("mouseout", () => {
            this.LinkToolTip.style("display", "none");});
    }

    else // Ancestor.
    {
      g.selectAll(".link")
        .data(treeData.links())
        .join("path")
        .attr("class", "link")
        .attr("marker-start", "url(#arrowhead-start)")
        .attr("d", d3.linkVertical()
            .x(d => d.x)
            .y(d => d.y))
        .style("stroke", d => getPersonRecordFromIndexes(d.source.data.ix).jesusTree && getPersonRecordFromIndexes(d.target.data.ix).jesusTree ? "red" : "#ccc")

	// 29-Jan-25: Add pop-up on links.
        .on("mouseover", (event, d) => {
	    let person = getPersonRecordFromIndexes(d.source.data.ix);
	    let father = "-" == person.father.augmentedUnifiedName ? "Unknown" : baseName(person.father.augmentedUnifiedName);
	    let mother = "-" == person.mother.augmentedUnifiedName ? "Unknown" : baseName(person.mother.augmentedUnifiedName);

            this.LinkToolTip // Need to fill in the pop-up so we know what its width is.
		.style("display", "block")
                .html(`<b>${person.baseNameFromUnifiedName}</b><br>Father: ${father}.<br>Mother: ${mother}.`);

	    const tooltipWidth = this.LinkToolTip.node().offsetWidth;
	    const svgRightEdge = document.querySelector("svg").getBoundingClientRect().right;
	    let leftPosition = event.pageX; // Assume we can put the tooltip to the right of the mouse position until we know otherwise.

	    if (leftPosition + tooltipWidth > svgRightEdge) // If that would overflow the right margin, move the position,
		leftPosition = event.pageX - tooltipWidth/2;

            this.LinkToolTip
                .style("left", (leftPosition + 5) + "px")
                .style("top", (event.pageY + 10) + "px");
        })
        .on("mouseout", () => {
            this.LinkToolTip.style("display", "none");});
    }

    // Render nodes
    const nodes = g.selectAll(".node")
        .data(treeData.descendants())
        .join("g")
        .attr("class", "node")
        .attr("fill", d => getPersonRecordFromIndexes(d.data.ix).jesusTree ? "red" : "black")
        .attr("id", d => "N" + d.data.ix.toString())
        .attr("transform", d => `translate(${d.x},${d.y})`);


    return nodes
  }
}
