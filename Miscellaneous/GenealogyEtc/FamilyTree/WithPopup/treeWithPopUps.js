/******************************************************************************/
/* Functionality
   =============

   - This displays a family tree rooted at a Bible individual of your choice.

   - The tree is always rooted at a selected individual, but you can choose
     between showing the descendant tree and the ancestor tree.

   - The display can be scrolled left or right or up or down, and zoomed using
     the mouse wheel.

   - There is a node for each individual, represented by a small circle.
     Clicking on that circle or the associated label brings up a window showing
     details of the individual.  The window remains visible until you cilck on
     it again.

   -  The window includes details of related individuals (siblings, spouse,
      etc).  If you click on one of these, the family tree is redrawn based
      upon that individual.

   - You can also select an individual from a drop-down.

   - When you select an individual, the node for that individual is
     highlighted.  When showing an ancestor tree, it is possible that the
     same individual may appear more than once, for example because they may
     be an ancoestor of both the mother and father of the individual.  In
     such a case, all instances of the individual are highlighted.

   - Trees can be drawn either horizontally or vertically.





   API
   ===

   - The code below starts with a configuration section which lets you specify
     default layout settings.

   - renderTreeForPerson draws the tree for a given individual.  The argument
     should be a fully qualified name -- eg 'Aaron@Exo.4.14-Heb'.

   - renderTreeForStrongs draws the tree for a given Strongs number (which
     equates to a given individual).

   - The code assumes a set-up like that supplied by index.html, and also
     assumes that my standard people.json file is available.





   Evaluation harness
   ==================

   At present the code is set up mainly as a proof-of-concept.  To get
   round limitations upon accessing files from a web page, for instance,
   it requires you to select the input file; and it supplies hooks so that
   you can alter the layout dynamically so as to find out what looks best.

   There are a number of issues to be resolved before this can be used in
   earnest:

   - On entry at present, the tree is drawn for the first person in the
     drop-down.  We may not wish to continue with the drop-down at all (it
     contains an awful lot of entries.

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

let Orientation = "v";
let LayoutHandlerH = null;
let LayoutHandlerV = null;

function getLayoutHandler ()
{
    if (null === LayoutHandlerH) LayoutHandlerH = new HorizontalLayoutHandler ( { spacingBetweenLayers: 200, spacingBetweenSiblings: 50, fontSizeForNames: "small" } );
    if (null === LayoutHandlerV) LayoutHandlerV = new VerticalLayoutHandler   ( { spacingBetweenLayers: 200, spacingBetweenSiblings: 50, fontSizeForNames: "small" } );

    if ("v" == Orientation)
	return LayoutHandlerV;
    else
	return LayoutHandlerH;
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

   qualifiedName: Fully qualified name -- eg 'Aaron@Exo.4.14-Heb'.
*/

function renderTreeForPerson (qualifiedName)
{
    renderTreeForPersonA(qualifiedName)
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
let CurrentPerson = null;
let Duplicates = null
let FamilyData = {};
let LayoutHandler;
let StrongsMap = new Map()
let QualifiedNamesList = [];


/******************************************************************************/
const svg = d3.select("svg");
const g = svg.append("g");

const zoom = d3.zoom()
   .scaleExtent([0.5, 2]) // Set zoom range
   .on("zoom", (event) => g.attr("transform", event.transform));
svg.call(zoom);


/******************************************************************************/
let offsetX = 0
let offsetY = 0
const drag = d3.drag()
  .on("start", function(event)
    {
      d3.select(this).raise();
      offsetX = event.x - parseFloat(d3.select(this).style("left"));
      offsetY = event.y - parseFloat(d3.select(this).style("top"));
    }
  )

  .on("drag", function(event)
    {
      d3.select(this)
        .style("left", `${event.x - offsetX}px`)
        .style("top", `${event.y - offsetY}px`);
    }
  )
  .on("end", function(event) { /* Optional: This is where you could add additional logic when the drag ends. */ });


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
        renderTreeForPersonA(CurrentPerson); // Redraw the tree based on the mode
    });
});


/******************************************************************************/
/* Handles the radio buttons which determine display orientation. */

const OrientationButtons = document.querySelectorAll('input[name="orientation"]');
OrientationButtons.forEach(radio => {
    radio.addEventListener("change", (event) => {
        Orientation = event.target.value;
        renderTreeForPersonA(CurrentPerson); // Redraw the tree based on the mode
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
    renderTreeForPersonA(CurrentPerson);
});


const SpacingBetweenGenerationsSlider = document.getElementById("spacingBetweenGenerationsSlider");
const SpacingBetweenGenerationsSliderValue = document.getElementById("spacingBetweenGenerationsSliderValue");

SpacingBetweenGenerationsSlider.addEventListener("input", (event) => {
    n = parseInt(event.target.value, 10);
    SpacingBetweenGenerationsSliderValue.textContent = n;
    getLayoutHandler().spacingBetweenLayers = n
    renderTreeForPersonA(CurrentPerson);
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
/* Builds an index of all people available for display.  Useful, for instance
   when handling Strongs input. */

function buildIndexes (data)
{
    for (let key in data) {
	let person = data[key]
	let ix = QualifiedNamesList.length;
	QualifiedNamesList.push(key)
	person.summaryOfSupplementaryData.allDStrongs.forEach((entry) => { StrongsMap.set(entry, ix) })
    }
}

    
/******************************************************************************/
/* Loads data from file.  This is needed only for this sample application; if
   something akin to this is ever put live, we'd need to pick the data up by
   a different means. */

function handleFile (event) {
    const file = event.target.files[0];
    const reader = new FileReader();
    reader.onload = function (e) {
        FamilyData = JSON.parse(e.target.result);
        populateDropdown(FamilyData);
	buildIndexes(FamilyData)
    };
    reader.readAsText(file);
}


/******************************************************************************/
function populateDropdown (data) {
    const personSelect = document.getElementById("personSelect");
    personSelect.innerHTML = ""; // Clear previous options
    Object.keys(data).forEach(person => {
        const option = document.createElement("option");
        option.value = person;
        option.textContent = person;
        personSelect.appendChild(option);
    });

    personSelect.addEventListener("change", () => {
        CurrentPerson = personSelect.value;
        renderTreeForPersonA(CurrentPerson);
    });



    // This displays the first person in the drop-down once the drop-down has
    // been populated.  We will presumably want to do something different in
    // future.
    
    personSelect.value = Object.keys(data)[0];
    CurrentPerson = personSelect.value;
    renderTreeForPersonA(CurrentPerson);
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
/**                            Implementation                                **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
/* Builds up the relationship data structure, but not the graphical
   representation.

   Note that the link field here apparently has to be called 'children' even
   though what we are actually dealing with is the parents. */

function buildAncestorHierarchy (rootPerson, data) {
    const person = data[rootPerson];
    if (!person) return null;

    parents = [person.father, person.mother].filter(x => x.augmentedUnifiedName !== "-")

    return {
        name: rootPerson,
	father: person.father,
	mother: person.mother,
	partners: person.partners,
	offspring: person.offspring,
	siblings: person.siblings,
	longDescription: person.longDescription.replaceAll("¶", "<p>"),
        children: parents.map(parent => buildAncestorHierarchy(parent.augmentedUnifiedName, data))
    };
}


/******************************************************************************/
/* Builds up the relationship data structure, but not the graphical
   representation. */

function buildDescendantHierarchy (rootPerson, data) {
    const person = data[rootPerson];
    if (!person) return null;

    return {
        name: rootPerson,
	father: person.father,
	mother: person.mother,
	partners: person.partners,
	offspring: person.offspring,
	siblings: person.siblings,
	longDescription: person.longDescription.replaceAll("¶", "<p>"),
        children: person.offspring.map(relative => buildDescendantHierarchy(relative.augmentedUnifiedName, data))
    };
}


/******************************************************************************/
/* Identifies duplicate people in the tree as currently displayed. */

function findDuplicates (root)
{
    const duplicates = new Map();
    
    root.each(node => {
      if (duplicates.has(node.data.name))
        duplicates.get(node.data.name).push(node); // Add to the list of duplicate nodes
      else
        duplicates.set(node.data.name, [node]); // Initialize with the first occurrence
    });

    return duplicates
}


/******************************************************************************/
function highlightNodeAndDuplicates (personName)
{
  unhighlightNodeAndDuplicates()
  d3.selectAll("circle").style("fill", d => d.data.name === personName ? "red" : "steelblue"); // Change duplicate color to red
  //d3.selectAll("text").style("fill", d => d.data.Name === personName ? "red" : "black"); // Change duplicate label color
}


/******************************************************************************/
function unhighlightNodeAndDuplicates ()
{
  d3.selectAll("circle").style("fill", "steelblue"); // Set the default color
}


/******************************************************************************/
function lookupStrongs (strongs)
{
    let ix = StrongsMap.get(strongs)
    return QualifiedNamesList[ix]
}

    
/******************************************************************************/
/* I need qualified names as id fields sometimes.  Unfortunately qualified
   names include @=signs and full-stops, and these are invalid in ids. */

function nameForUseAsKey (name)
{
    return name.replace("@", "_").replaceAll(".", "_")
}


/******************************************************************************/
/* Called on clicking a node or the associated text label.  Creates the
   hoverbox to show the content, and arranges for it to be draggable,
   clickable, etc. */

function nodeOnClick (event, d)
{
  /****************************************************************************/
  const key = nameForUseAsKey(d.data.name)
  const existingHoverBox = d3.select(`#hoverbox-${key}`)
  if (!existingHoverBox.empty())
  {
      unhighlightNodeAndDuplicates();
      existingHoverBox.remove();
      return;
  }



  /****************************************************************************/
  highlightNodeAndDuplicates(d.data.name);
    


  /****************************************************************************/
  const fatherName = d.data.father.augmentedUnifiedName
  const motherName = d.data.mother.augmentedUnifiedName
  const partners   = d.data.partners || [];
  const offspring  = d.data.offspring || [];
  const siblings   = d.data.siblings || [];
  const longDescription = d.data.longDescription
  const personIndex = StrongsMap;
  const svgRect = svg.node().getBoundingClientRect();

  const hoverBox = d3.select("body")
    .append("div")
    .style("position", "absolute")
    .style("background", "rgba(255, 255, 128 )")
    .style("border", "1px solid #ccc")
    .style("padding", "10px")
    .style("border-radius", "5px")
    .style("box-shadow", "0px 4px 6px rgba(0, 0, 0, 0.1)")
    .style("font-size", "small")
    .style("max-width", "450px")
    .style("word-wrap", "break-word")
    .style("max-height", "200px")
    .style("overflow-y", "auto")
    .style("z-index", "1000")
    .attr("id", `hoverbox-${key}`)
    .style("top", `${event.pageY}px`)
    .style("left", `${event.pageX}px`)
//        .style("top", `${svgRect.bottom}px`) // Align with the bottom of the SVG
//        .style("left", `${svgRect.left}px`) // Optionally align it with the left edge
    ;

    hoverBox.append("div")
        .attr("class", "hover-box-close")
        .style("position", "absolute")
        .style("top", "5px")
        .style("right", "5px")
        .style("cursor", "pointer")
        .style("background", "red") // Optional: highlight close button
        .style("color", "white")
        .style("padding", "2px 5px")
        .style("border-radius", "3px")
        .text("✖") // Close button symbol
        .on("click", () => { hoverBox.remove(); }); // $$$ Unhighlight node associated with hover box.

    hoverBox.call(drag);

    hoverBox.append("div")
      .html(`
            <p style='align:center;color:green'>${unqualifiedName(d.data.name)} (first mentioned at ${d.data.name.split("@")[1].split("-")[0]})</p>
            <p>${longDescription}</p>
            <p><b>Father:</b> ${fatherName !== "-" ? `<span class="father-link simulated-link">${unqualifiedName(fatherName)}</span>` : "Unknown"}
            <p><b>Mother:</b> ${motherName !== "-" ? `<span class="mother-link simulated-link">${unqualifiedName(motherName)}</span>` : "Unknown"}
            <p><b>Siblings:</b>  ${siblings.length  > 0 ? siblings.map (sibling => `<span class="sibling-link simulated-link">${unqualifiedName(sibling.augmentedUnifiedName)}</span>`).join(", ") : "-" }</p>
            <p><b>Partners:</b>  ${partners.length  > 0 ? partners.map (partner => `<span class="partner-link simulated-link">${unqualifiedName(partner.augmentedUnifiedName)}</span>`).join(", ") : "-" }</p>
            <p><b>Offspring:</b> ${offspring.length > 0 ? offspring.map(kid     => `<span class="offspring-link simulated-link">${unqualifiedName(kid.augmentedUnifiedName)}</span>`).join(", ") : "-" }</p>
      `);

    if (fatherName !== "-") { hoverBox.select(".father-link").on("click", () => { renderTreeForPersonA(fatherName); }); }
    if (motherName !== "-") { hoverBox.select(".mother-link").on("click", () => { renderTreeForPersonA(motherName); }); }
    siblings .forEach((sibling, index) => { hoverBox.selectAll(".sibling-link")  .filter((d, i) => i === index).on("click", () => { renderTreeForPersonA(sibling.augmentedUnifiedName); }); });
    partners .forEach((partner, index) => { hoverBox.selectAll(".partner-link")  .filter((d, i) => i === index).on("click", () => { renderTreeForPersonA(partner.augmentedUnifiedName); }); });
    offspring.forEach((kid,     index) => { hoverBox.selectAll(".offspring-link").filter((d, i) => i === index).on("click", () => { renderTreeForPersonA(kid    .augmentedUnifiedName); }); });
    showHoverBox(event, hoverBox)
}


/******************************************************************************/
/* Draws the tree and createst click handlers etc. */

function renderTreeForPersonA (rootPerson)
{
  /****************************************************************************/
  unhighlightNodeAndDuplicates()


    
  /****************************************************************************/
  const root = "descendants" == ViewMode ? buildDescendantHierarchy(rootPerson, FamilyData) : buildAncestorHierarchy(rootPerson, FamilyData);
  let layoutHandler = getLayoutHandler()
  const treeLayout = d3.tree()
    .nodeSize([layoutHandler.spacingBetweenSiblings, layoutHandler.spacingBetweenLayers])
    .separation((a, b) => (a.parent === b.parent ? 1 : 1.5)); // More spacing between unrelated nodes
  const treeData = treeLayout(d3.hierarchy(root));



  /****************************************************************************/
  g.selectAll("*").remove(); // Clear previous tree



  /****************************************************************************/
  /* Draw tree. */

  const nodes = layoutHandler.renderLinksAndNodes(treeData)
  Duplicates = findDuplicates(nodes)
  nodes.append("circle").attr("r", 5)
  layoutHandler.positionText(nodes);



  /****************************************************************************/
  /* Add functionality for the hover box. */
    
  nodes.on("click", nodeOnClick);
  nodes.style("cursor", "pointer");
  textNodes = svg.selectAll("text");
  textNodes.style("cursor", "pointer");
  textNodes.style("font-size", layoutHandler.fontSizeForNames);
  layoutHandler.adjustPositionOfRootNode(textNodes, root);
}


/******************************************************************************/
function showHoverBox (event, hoverBox)
{
    // Temporarily set visibility to hidden to measure dimensions
    const hoverBoxNode = hoverBox.node();
    hoverBoxNode.style.visibility = "hidden";
    const hoverBoxWidth = hoverBoxNode.offsetWidth;
    const hoverBoxHeight = hoverBoxNode.offsetHeight;
    hoverBoxNode.style.visibility = "visible";

    // Get viewport dimensions
    const viewportWidth = window.innerWidth;
    const viewportHeight = window.innerHeight;

    // Calculate initial position (offset from mouse)
    let x = event.pageX + 10; // Offset slightly to the right
    let y = event.pageY + 10; // Offset slightly below

    // Adjust position to prevent overflow
    if (x + hoverBoxWidth > viewportWidth) {
        x = viewportWidth - hoverBoxWidth - 10; // Align with right edge
    }
    if (y + hoverBoxHeight > viewportHeight) {
        y = viewportHeight - hoverBoxHeight - 10; // Align with bottom edge
    }
    if (y < 0) {
        y = 10; // Prevent it from going above the top
    }

    // Apply the calculated position
    hoverBox.style("left", `${x}px`).style("top", `${y}px`);
}




/******************************************************************************/
function unqualifiedName (name)
{
  return name.split("@")[0]
}





/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
class HorizontalLayoutHandler
{
  /****************************************************************************/
  constructor ( {spacingBetweenLayers = 0, spacingBetweenSiblings = 0, fontSizeForNames = "small" } = {})
  {
    this.spacingBetweenLayers = spacingBetweenLayers
    this.spacingBetweenSiblings = spacingBetweenSiblings
    this.fontSizeForNames = fontSizeForNames
  }

    
  /****************************************************************************/
  /* Positions the root node so that the tree sits reasonably well within the
     available space. */

  adjustPositionOfRootNode (textNodes, root)
  {
    textNodes
      .each(function () {
	if (this.textContent == unqualifiedName(root.name))
	{
	  const boundingRect = this.getBoundingClientRect()
          const labelWidth = boundingRect.width;
          const newX = labelWidth + 20; // 20 to give a bit of padding at the left.

	  const svgHeight = svg.node().getBoundingClientRect().height; // Height of SVG container.
          const rootY = 0
	  const newY = svgHeight / 2 - rootY;
          const labelHeight = this.getBoundingClientRect().height;
          const initialY = labelHeight + 20;

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
      .attr("dy", 3) // Vertical offset
      .attr("text-anchor", d => d.children ? "middle" : "start") // Align text properly for side position
      
    nodes.append("text")
      .attr("dy", 3) // Vertical offset

      .attr("text-anchor", d => {
            // If no children, align the text to the start (left)
            if (!d.children) return "start";
            // If no parent (root), align the text to the end (right)
            return d.parent ? "middle" : "end";
      })

      .attr("transform", d => {
            // If node has no parent or no children, place text beside the node
            const horizontalOffset = 10; // Smaller gap for both root and leaf nodes
            if (!d.parent || !d.children) {
                return `translate(${d.children ? -horizontalOffset : horizontalOffset}, 0)`; 
            }

            // Check for previous and later siblings
            const siblings = d.parent ? d.parent.children : [];
            const nodeIndex = siblings.indexOf(d.data);
            const hasPreviousSibling = nodeIndex > 0;
            const hasLaterSibling = nodeIndex < siblings.length - 1;

            // Logic to determine name position
            if (!hasPreviousSibling && !hasLaterSibling) {
                // No previous or later sibling, place name beside the node
                return `translate(${d.children ? -20 : 20}, 0)`; // Adjust X position
            } else if (!hasPreviousSibling && hasLaterSibling) {
                // No previous sibling, has later sibling, place name below
                const yOffset = 20; // Adjust this value if needed
                return `translate(0, ${yOffset})`; // Place name below
            } else if (hasPreviousSibling && !hasLaterSibling) {
                // Has previous sibling, no later sibling, place name beside
                return `translate(${d.children ? -20 : 20}, 0)`; // Adjust X position
            } else {
                // Has both previous and later siblings, place name below with enough space
                const yOffset = 20; // Adjust this value if needed
                return `translate(0, ${yOffset})`; // Place name below
            }
        })
       .text(d => unqualifiedName(d.data.name));
  }


  /****************************************************************************/
  /* Positions the root node so that the tree sits reasonably well within the
     available space. */

  renderLinksAndNodes (treeData)
  {
    // Render links
    g.selectAll(".link")
        .data(treeData.links())
        .join("path")
        .attr("class", "link")
        .attr("d", d3.linkHorizontal()
            .x(d => d.y)
            .y(d => d.x));

    // Render nodes
    const nodes = g.selectAll(".node")
        .data(treeData.descendants())
        .join("g")
        .attr("class", "node")
        .attr("transform", d => `translate(${d.y},${d.x})`);

    return nodes
  }
}





/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
class VerticalLayoutHandler
{
  /****************************************************************************/
  constructor ( {spacingBetweenLayers = 0, spacingBetweenSiblings = 0, fontSizeForNames = "small" } = {})
  {
    this.spacingBetweenLayers = spacingBetweenLayers
    this.spacingBetweenSiblings = spacingBetweenSiblings
    this.fontSizeForNames = fontSizeForNames
  }

    
  /****************************************************************************/
  /* Positions the root node so that the tree sits reasonably well within the
     available space. */

  adjustPositionOfRootNode (textNodes, root)
  {
    textNodes
      .each(function () {
	  if (this.textContent == unqualifiedName(root.name))
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
	  .text(d => unqualifiedName(d.data.name));
  }


  /****************************************************************************/
  /* Positions the root node so that the tree sits reasonably well within the
     available space. */

  renderLinksAndNodes (treeData)
  {
    // Render links
    g.selectAll(".link")
        .data(treeData.links())
        .join("path")
        .attr("class", "link")
        .attr("d", d3.linkVertical()
            .x(d => d.x)
            .y(d => d.y));

    // Render nodes
    const nodes = g.selectAll(".node")
        .data(treeData.descendants())
        .join("g")
        .attr("class", "node")
        .attr("transform", d => `translate(${d.x},${d.y})`);

    return nodes
  }
}
