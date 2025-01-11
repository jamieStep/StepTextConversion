import net.sf.saxon.lib.NamespaceConstant
import org.stepbible.tyndaleStudyNotesEtc.Dbg
import org.w3c.dom.*
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.IntConsumer
import java.util.function.Predicate
import java.util.regex.Pattern
import java.util.stream.IntStream
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMResult
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import kotlin.collections.ArrayList
import kotlin.collections.HashSet


/******************************************************************************/
/**
 * Provides an interface to a standard XML parse tree (a Document).  See also
 * **DomExtensionFunctions**.
 *
 * @author ARA "Jamie" Jamieson
 */

object Dom
{
    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                                Public                                  **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    /**
     * Adds a list of children to the existing children of a given node.  Note
     * that there is no need for the children and the new parent to be in the same
     * document.
     *
     * @param parent Parent node.
     * @param newChildren Children to be added (null or empty are ok).
     */

    fun addChildren (parent: Node, newChildren: List<Node>?)
    {
        if (newChildren.isNullOrEmpty()) return

        val parentOwnerDocument = parent.ownerDocument
        if (newChildren[0].ownerDocument === parentOwnerDocument)
            newChildren.forEach { n: Node? -> parent.appendChild(n) }
        else
            newChildren.forEach { n: Node -> parent.appendChild(createNode(parentOwnerDocument, n, true)) }
    }


    /****************************************************************************/
    /**
     * Adds a new text value to a node.
     *
     * @param node Node.
     * @param text Text string.
     */

    fun addTextNodeToNode (node: Node, text: String?)
    {
        val txt = node.ownerDocument.createTextNode(text)
        node.appendChild(txt)
    }


   /****************************************************************************/
    /**
     * Determines whether all siblings prior to a given node satisfy a given
     * condition.  (If there are no prior siblings, the return value is
     * determined by ifNoPriorSiblings.
     *
     * @param child Child node.
     * @param test Test to apply.
     * @return True if all preceding siblings satisfy the test.
     */

    fun allFollowingSiblingsSatisfy (child: Node, test: Predicate<Node>, ifNoFollowingSiblings: Boolean = true): Boolean
    {
        val children = child.parentNode.childNodes
        var doTest = false
        for (i in 0..< children.length)
        {
            val n = children.item(i)
            if (n === child)
              doTest = true
            else if (doTest && !test.test(n))
              return false
        }

        return ifNoFollowingSiblings
    }


   /****************************************************************************/
    /**
     * Determines whether all siblings prior to a given node satisfy a given
     * condition.  (If there are no prior siblings, the return value is
     * determined by ifNoPriorSiblings.
     *
     * @param child Child node.
     * @param test Test to apply.
     * @return True if all preceding siblings satisfy the test.
     */

    fun allPrecedingSiblingsSatisfy (child: Node, test: Predicate<Node>, ifNoPriorSiblings: Boolean = true): Boolean
    {
        val children = child.parentNode.childNodes
        for (i in 0..< children.length)
        {
            val n = children.item(i)
            if (n === child) return true
            if (!test.test(n)) return false
        }

        return ifNoPriorSiblings
    }


    /****************************************************************************/
    /**
     * Checks if two nodes, one a descendant of the other, are separated by at most
     * whitespace.
     *
     * @param ancestor Start node.
     * @param descendant End node.
     * @return True if separated by whitespace only.
     */

    fun ancestorAndDescendantAreSeparatedByWhitespaceOnly (ancestor: Node, descendant: Node): Boolean
    {
        val tempNode: Node = createTextNode(ancestor.ownerDocument, "_XXX")
        insertNodeBefore(descendant, tempNode)
        val s = ancestor.textContent.trim { it <= ' ' }
        val ix = s.indexOf("_XXX")
        deleteNode(tempNode)
        return 0 == ix
    }


  /****************************************************************************/
  /**
  * Applies XSLT processing to a given document.  This attempts to relieve the
  * user of the boring bits of markdown like the standard header and trailer,
  * and the chunk you normally need in order to copy across to the output
  * elements which aren't affected by the transformation.  All you need is
  * a series of chunks something like:
  *
  * ```
  *       <xsl:template match="verse">
  *         <jamie>
  *            <xsl:apply-templates select='@* | node()'/>
  *         </jamie>
  *     </xsl:template>
  * ```
  *
  *
  * This takes care of header and trailer, and also default namespace.  If you
  * need to do more sophisticated things, you'll need to create a fully-fledged
  * stylesheet for yourself, including all the nasty boring bits, and call
  * [applyStylesheet] instead.
  *
  * IMPORTANT: Note that this returns a *NEW* document -- it doesn't update the
  * document in situ.
  *
  * @param document The document to be processed.
  * @param basicStylesheet The stylesheet.
  * @return Modified document.
  */

  fun applyBasicStylesheet (document: Document, basicStylesheet: String) =
    applyStylesheet(document, expandBasicStylesheet(document, basicStylesheet))


  /****************************************************************************/
  /**
  * Applies XSLT processing to a given document.  This attempts to relieve the
  * user of the boring bits of markdown like the standard header and trailer,
  * and the chunk you normally need in order to copy across to the output
  * elements which aren't affected by the transformation.  All you need is
  * a series of chunks something like:
  *
  * ```
  *       <xsl:template match="verse">
  *         <jamie>
  *            <xsl:apply-templates select='@* | node()'/>
  *         </jamie>
  *     </xsl:template>
  * ```
  *
  *
  * This takes care of header and trailer, and also default namespace.  If you
  * need to do more sophisticated things, you'll need to create a fully-fledged
  * stylesheet for yourself, including all the nasty boring bits, and call
  * [applyStylesheet] instead.
  *
  * <span class='important'>IMPORTANT:</span> Note that this updates the document
  * in situ.
  *
  * @param node Node to which, along with its descendants, the stylesheet is applied.
  * @param basicStylesheet The stylesheet.
  * @return Modified document.
  */

  fun applyBasicStylesheet (node:Node, basicStylesheet: String) =
    applyStylesheet(node, expandBasicStylesheet(node.ownerDocument, basicStylesheet))


  /****************************************************************************/
  /**
  * Takes a given document and applies an XSLT stylesheet to it.  The stylesheet
  * must be 'complete' -- ie contained within xsl:stylesheet and with anything
  * necessary to handle namespaces and to copy material which is not subject to
  * modification by the spreadsheet (assuming you want to do that).  For a
  * slightly easier approach -- where circumstances permit -- see
  * applyBasicStylesheet.
  *
  * <span class='important'>IMPORTANT:</span> Note that this returns a *NEW*
  * document -- it doesn't update the document in situ.
  *
  * @param document The document to be processed.
  * @param stylesheet The stylesheet.
  * @return Modified document.
  */

  fun applyStylesheet (document: Document, stylesheet: String): Document
  {
    try
    {
      val transformerFactory = TransformerFactory.newInstance()
      val styleSource = StreamSource(StringReader(stylesheet))
      val transformer = transformerFactory.newTransformer(styleSource)
      val source = DOMSource(document)
      val newDom = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
      val result = DOMResult(newDom)
      transformer.transform(source, result)
      return newDom
    }
    catch (e:Exception)
    {
      throw StepExceptionWithStackTraceAbandonRun(e.message + "\n\n" + stylesheet)
    }
  }


  /****************************************************************************/
  /**
  * Takes a given document and applies an XSLT stylesheet to it.  The stylesheet
  * must be 'complete' -- ie contained within xsl:stylesheet and with anything
  * necessary to handle namespaces and to copy material which is not subject to
  * modification by the spreadsheet (assuming you want to do that).  For a
  * slightly easier approach -- where circumstances permit -- see
  * applyBasicStylesheet.
  *
  * <span class='important'>IMPORTANT:</span> Note that this updates the document
  * in situ.
  *
  * @param node Node to which (and to its descendants) the stylesheet is applied.
  * @param stylesheet The stylesheet.
  * @return Modified document.
  */

  fun applyStylesheet (node: Node, stylesheet: String): Document
  {
    try
    {
      val transformerFactory = TransformerFactory.newInstance()
      val styleSource = StreamSource(StringReader(stylesheet))
      val transformer = transformerFactory.newTransformer(styleSource)
      val source = DOMSource(node)
      val result = DOMResult()
      transformer.transform(source, result)
      node.parentNode.replaceChild(node.ownerDocument.importNode(result.node.firstChild, true), node)
      return node.ownerDocument
    }
    catch (e:Exception)
    {
      throw StepExceptionWithStackTraceAbandonRun(e.message + "\n\n" + stylesheet)
    }
  }


  /****************************************************************************/
  /**
  * Clones an enture DOM.
  *
  * @param doc DOM to be cloned.
  * @return Cloned doc.
  */

  fun cloneDocument (doc: Document): Document
  {
    val res = createDocument()
    val n = res.importNode(doc.documentElement, true)
    res.appendChild(n)
    return res
  }


    /****************************************************************************/
    /**
     * Clones a collection of nodes.
     *
     * @param nodes List of nodes to be cloned.
     * @return Cloned list.
     */

    fun cloneNodes (nodes: List<Node>): List<Node>
    {
        return nodes.map { it.cloneNode(true) }
    }


    /****************************************************************************/
    /**
     * Clones a single node.  This version potentially clones the nodes
     * in a different document.
     *
     * @param doc Target document.
     * @param node Node to be cloned.
     * @param deep True if the substructure of the nodes is to be cloned.
     * @return Cloned node.
     */

    fun cloneNode (doc: Document, node: Node, deep: Boolean = true): Node
    {
        return createNode(doc, node, deep)
    }


    /****************************************************************************/
    /**
     * Clones a collection of nodes.  This version potentially clones the nodes
     * in a different document.
     *
     * @param doc Target document.
     * @param nodes List of nodes to be cloned.
     * @param deep True if the substructure of the nodes is to be cloned.
     * @return Cloned list.
     */

    fun cloneNodes (doc: Document, nodes: List<Node>, deep: Boolean = true): List<Node>
    {
        return nodes.map { createNode(doc, it, deep) }
    }


    /****************************************************************************/
    /**
     * Does what it says on the tin.
     *
     * @param node Node.
     */

    fun convertToSelfClosingNode (node: Node)
    {
        promoteChildren(node)
    }


    /****************************************************************************/
    /**
     * Copies attributes from one node to another.
     *
     * @param target Target node.
     * @param source Source node.
     */

    fun copyAttributes (target: Node, source: Node)
    {
        val attributes = getAttributes(source)
        setAttributes(target, attributes)
    }



    /****************************************************************************/
    /**
     * Creates a comment node.
     *
     * @param doc Document within which comment is to appear.
     * @param content Content of comment.
     * @return Comment node.
     */

    fun createCommentNode (doc: Document, content: String): Comment
    {
        return doc.createComment(content)
    }


  /****************************************************************************/
  /**
  * Creates and returns an empty document.
  *
  * @return Document.
  */

  fun createDocument (): Document
  {
    System.setProperty("javax.xml.xpath.XPathFactory:" + NamespaceConstant.OBJECT_MODEL_SAXON, "net.sf.saxon.xpath.XPathFactoryImpl")
    val factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true // See comments above.
    factory.isIgnoringComments = false
    factory.isIgnoringElementContentWhitespace = false
    val builder: DocumentBuilder = factory.newDocumentBuilder()
    return builder.newDocument()
  }


    /****************************************************************************/
    /**
     * Creates a text node.
     *
     * @param doc Document within which comment is to appear.
     * @param content Content of comment.
     * @return Comment node.
     */

    fun createTextNode (doc: Document, content: String): Text
    {
        return doc.createTextNode(content)
    }


    /****************************************************************************/
    /**
     * Creates a new node by cloning an existing one.
     *
     * @param doc Document within which node is created.
     * @param node Node to be copied.
     * @param andSubstructure If true, all descendants are also copied.
     * @return New node.
     */

    fun createNode (doc: Document, node: Node, andSubstructure: Boolean): Node
    {
        val tagName = getNodeName(node)
        if ("#text" == tagName) return doc.createTextNode(node.textContent)
        if ("#comment" == tagName) return doc.createComment(node.textContent)
        val res = createNode(doc, tagName, getAttributes(node))
        if (!andSubstructure) return res
        val nl = node.childNodes
        for (i in 0..< nl.length) res.appendChild(createNode(doc, nl.item(i), true))
        return res
    }


    /****************************************************************************/
    /**
     * Creates a new node (*except* a comment node -- the routine doesn't
     * handle them).
     *
     * @param doc Document within which node is created.
     * @param nodeName Name to be given to node.
     * @param attributes Attributes (supply null if no attributes).
     * @return New node.
     */

    fun createNode (doc: Document, nodeName: String, attributes: Map<String, String>?): Node
    {
        val e = doc.createElement(nodeName)
        val n = e as Node
        attributes?.keys?.forEach { setAttribute(n, it, attributes[it]!!) }
        return n
    }


    /****************************************************************************/
    /**
     * Turns an XML string into a node.
     *
     * @param doc Owning document.
     * @param xml XML.
     * @return Node.
     * @throws StepExceptionWithStackTraceAbandonRun For example if the XML is invalid.
     */

    fun createNode (doc: Document, xml: String): Node
    {
        return try
        {
            val n: Node = DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(ByteArrayInputStream(xml.toByteArray()))
                .documentElement
            doc.importNode(n, true)
        }
        catch (e: Exception)
        {
            throw StepExceptionWithStackTraceAbandonRun(e)
        }
    }


    /****************************************************************************/
    /**
     * Deletes an all attributes.
     *
     * @param node Node.
     */

    fun deleteAllAttributes (node: Node)
    {
        if (!node.hasAttributes()) return
        val attributes = getAttributes(node)
        attributes.keys.forEach { x -> deleteAttribute(node, x) }
    }


    /****************************************************************************/
    /**
     * Deletes all the text from a given node.
     *
     * @param node Node of interest.
     */

    fun deleteAllTextFromNode (node: Node)
    {
        val nl = node.childNodes ?: return
        val nodes: MutableList<Node> = ArrayList()
        for (i in 0..< nl.length)
        {
            val n = nl.item(i)
            if (n is Text) nodes.add(n)
        }

        for (i in nodes.indices) deleteNode(nodes[i])
    }


    /****************************************************************************/
    /**
     * Deletes an attribute.  Does not worry if the attribute does not exist.
     *
     * @param node Node.
     * @param attributeName Attribute name.
     */

    fun deleteAttribute(node: Node, attributeName: String) {
        try
        {
            (node as Element).removeAttribute(attributeName)
        }
        catch (_: Exception)
        {
        }
    }


    /****************************************************************************/
    /**
     * Deletes an attribute.  Does not worry if the attribute does not exist.
     *
     * @param node Node.
     * @param pattern Pattern for attribute names.
     */

    fun deleteAttributesMatching (node: Node, pattern: String)
    {
        val pat = Pattern.compile(pattern)
        val attribs = getAttributes(node)
        attribs.keys.filter { pat.matcher(it).matches() }.forEach { deleteAttribute(node, it) }
    }



    /****************************************************************************/
    /**
     * Deletes all the children of a given node.
     *
     * @param node Node whose children are to be deleted.
     */

    fun deleteChildren (node: Node)
    {
        val nl = node.childNodes
        for (i in nl.length - 1 downTo 0) deleteNode(nl.item(i))
    }


    /****************************************************************************/
    /**
     * Deletes consecutive whitespace sibling nodes between but excluding two
     * given nodes, giving up as soon as a non-whitespace node is encountered.
     *
     * @param a First node.
     * @param b Second node.
     */

    fun deleteConsecutiveWhitespaceBetween (a: Node, b: Node)
    {
        val delenda: MutableList<Node> = ArrayList()
        var n = a
        while (b !== n.nextSibling.also { n = it })
        {
            if (isWhitespace(n)) delenda.add(n) else break
        }
        delenda.stream().forEach { x: Node -> deleteNode(x) }
    }


    /****************************************************************************/
    /**
     * Deletes a given node, taking its children with it.
     *
     * @param node Node to be deleted.
     * @return The node which has been deleted.
     */

    fun deleteNode (node: Node): Node
    {
        getParent(node)?.removeChild(node)
        return node
    }


    /****************************************************************************/
    /**
     * Deletes a given node, taking its children with it.  If the parent itself
     * now has no children, delete that -- and so on up the tree.
     *
     * @param node Node to be deleted.
     * @return List of all nodes deleted from parse tree.
     */

    fun deleteNodeRecursivelyUpwards (node: Node): List<Node>
    {
        val res: MutableList<Node> = ArrayList()
        var myNode = node

        while (true)
        {
          val parent = getParent(myNode)
          res.add(myNode)
          deleteNode(myNode)
          if (null == parent|| getChildren(parent).any { !isWhitespace(it) } ) break
          myNode = parent
        }

        return res
    }


    /****************************************************************************/
    /**
     * Deletes a collection of nodes recursively.
     *
     * @param nodes List of nodes to be cloned.
     */

    fun deleteNodes (nodes: List<Node>)
    {
        val safeDelete = Consumer { node: Node ->
            try
            {
                getParent(node)?.removeChild(node)
            }
            catch (_: Exception)
            {
            }
        }

        nodes.forEach { node: Node -> safeDelete.accept(node) }
    }


    /****************************************************************************/
    /**
     * Finds all the text nodes under a given node.  You don't seem to be able
     * to use, for this purpose, the other methods I have set up, because they
     * generally rely upon checking the names of nodes, and text nodes apparently
     * don't really have a name.
     *
     * @param n Node under which we look for text nodes.  This node is excluded
     *          from the output even if it is itself a text node.
     *
     * @return List of text nodes (possibly empty).
     */

    fun findAllTextNodes (n: Node): List<Node>
    {
        val res: MutableList<Node> = ArrayList()
        val children = n.childNodes
        for (i in 0..< children.length)
        {
            val child = children.item(i)
            if (child.nodeType == Node.TEXT_NODE) res.add(child) else res.addAll(findAllTextNodes(child))
        }

        return res
    }


    /****************************************************************************/
    /**
     * Returns the closest ancestor of a given node having a given name, or null.
     *
     * @param descendant Node from which we work.
     * @param nodeName Node name of ancestor.
     * @return Ancestor.
     */

    fun findAncestorByNodeName (descendant: Node, nodeName: String): Node?
    {
        var n: Node? = descendant
        while (true)
        {
            n = getParent(n!!)
            if (null == n) break
            if (nodeName == getNodeName(n)) return n
        }

        return null
    }


    /****************************************************************************/
    /**
     * Returns a list of all comment nodes.
     */

    fun findCommentNodes (doc: Document): List<Comment>
    {
      return getAllNodesBelow(doc).filter { it.nodeType == Node.COMMENT_NODE } .map { it as Comment}
    }


    /****************************************************************************/
    /**
     * Returns a list of nodes of a given type having an attribute with a given
     * name.
     *
     * @param doc Owning document.
     * @param nodeName Node type.
     * @param attributeName Name of attribute.
     * @return Node list or null.
     */

    fun findNodesByAttributeName (doc: Document, nodeName: String, attributeName: String): List<Node>
    {
        return findNodesByAttributeName(doc.documentElement, nodeName, attributeName)
    }


    /****************************************************************************/
    /**
     * Returns a list of nodes of a given type having an attribute with a given
     * name.
     *
     * @param n Owning node.
     * @param nodeName Node type.
     * @param attributeName Name of attribute.
     * @return Node list or null.
     */

    fun findNodesByAttributeName (n: Node?, nodeName: String, attributeName: String, xPathConstant: javax.xml.namespace.QName = XPathConstants.NODESET): List<Node>
    {
        return try
        {
            val xPathFactory = XPathFactory.newInstance()
            val xPath = xPathFactory.newXPath()
            //val expr = xPath.compile(".//$nodeName[@$attributeName]")
            val expr = xPath.compile(".//*[local-name()='$nodeName' and @$attributeName]")
            val res = expr.evaluate(n, xPathConstant) as NodeList
            toListOfNodes(res)
        }
        catch (e: Exception)
        {
            throw StepExceptionWithStackTraceAbandonRun(e)
        }
    }


    /****************************************************************************/
    /**
     * Returns the first node having a given attribute name.
     *
     * @param doc Owning document.
     * @param nodeName Node type.
     * @param attributeName Name of attribute.
     * @return Node or null.
     */

    fun findNodeByAttributeName (doc: Document, nodeName: String, attributeName: String): Node?
    {
        return findNodeByAttributeName(doc.documentElement, nodeName, attributeName)
    }


    /****************************************************************************/
    /**
     * Returns the first node having a given attribute name.
     *
     * @param n Ancestor node.
     * @param nodeName Node type.
     * @param attributeName Name of attribute.
     * @return Node or null.
     */

    fun findNodeByAttributeName (n: Node, nodeName: String, attributeName: String): Node?
    {
        return try
        {
            val xPathFactory = XPathFactory.newInstance()
            val xPath = xPathFactory.newXPath()
            val expr = xPath.compile(".//*[local-name()='$nodeName' and @$attributeName][1]")
            expr.evaluate(n, XPathConstants.NODE) as Node
        }
        catch (e: Exception)
        {
            return null
        }
    }


    /****************************************************************************/
    /**
     * Finds the first node of a given type having a given attribute with a given
     * value.  Matching is case-insensitive.
     *
     * @param doc Owning document.
     * @param nodeName Node type.
     * @param attributeName Name of attribute.
     * @param value Exact value of attribute (except case-insensitive).
     * @return Node or null.
     */

    fun findNodeByAttributeValue (doc: Document, nodeName: String, attributeName: String, value: String): Node?
    {
      return findNodeByAttributeValue(doc.documentElement, nodeName, attributeName, value)
    }


    /****************************************************************************/
    /**
     * Finds the first node of a given type having a given attribute with a given
     * value.  Matching is case-insensitive.
     *
     * @param parent Node below which other nodes are located.
     * @param nodeName Node type.
     * @param attributeName Name of attribute.
     * @param value Exact value of attribute (except case-insensitive).
     * @return Node or null.
     */

    fun findNodeByAttributeValue (parent: Node, nodeName: String, attributeName: String, value: String): Node?
    {
       return findNodeByAttributeValueInternal(parent, nodeName, attributeName, value)
    }


    /****************************************************************************/
    /**
     * Finds the first node of a given type having a given attribute with a given
     * value.  Matching is case-insensitive.
     *
     * @param doc Owning document.
     * @param nodeName Node type.
     * @param attributeName Name of attribute.
     * @param value Value of attribute as Regex.
     * @return Node or null.
     */

    fun findNodeByAttributeValue (doc: Document, nodeName: String, attributeName: String, value: Regex): Node?
    {
      return findNodeByAttributeValueInternal(doc.documentElement, nodeName, attributeName, value)
    }


    /****************************************************************************/
    /**
     * Finds the first node of a given type having a given attribute with a given
     * value.  Matching is case-insensitive.
     *
     * @param parent Node below which search occurs.
     * @param nodeName Node type.
     * @param attributeName Name of attribute.
     * @param value Value of attribute as Regex.
     * @return Node or null.
     */

    fun findNodeByAttributeValue (parent: Node, nodeName: String, attributeName: String, value: Regex): Node?
    {
      return findNodeByAttributeValueInternal(parent, nodeName, attributeName, value)
    }


    /****************************************************************************/
    /**
     * Finds the first node of a given type having a given attribute with a given
     * value.  Matching is case-insensitive.
     *
     * @param doc Owning document.
     * @param nodeName Node type.
     * @param attributeName Name of attribute.
     * @param value Exact value of attribute (except case-insensitive).
     * @return Node or null.
     */

    fun findNodesByAttributeValue (doc: Document, nodeName: String, attributeName: String, value: String): List<Node>
    {
      return findNodesByAttributeValueInternal(doc.documentElement, nodeName, attributeName, value)
    }


    /****************************************************************************/
    /**
     * Finds the first node of a given type having a given attribute with a given
     * value.  Matching is case-insensitive.
     *
     * @param parent Node below which other nodes are located.
     * @param nodeName Node type.
     * @param attributeName Name of attribute.
     * @param value Exact value of attribute (except case-insensitive).
     * @return Node or null.
     */

    fun findNodesByAttributeValue (parent: Node, nodeName: String, attributeName: String, value: String): List<Node>
    {
       return findNodesByAttributeValueInternal(parent, nodeName, attributeName, value)
    }


    /****************************************************************************/
    /**
     * Finds the first node of a given type having a given attribute with a given
     * value.  Matching is case-insensitive.
     *
     * @param doc Owning document.
     * @param nodeName Node type.
     * @param attributeName Name of attribute.
     * @param value Value of attribute as Regex.
     * @return Node or null.
     */

    fun findNodesByAttributeValue (doc: Document, nodeName: String, attributeName: String, value: Regex): List<Node>
    {
      return findNodesByAttributeValueInternal(doc.documentElement, nodeName, attributeName, value)
    }


    /****************************************************************************/
    /**
     * Finds the first node of a given type having a given attribute with a given
     * value.  Matching is case-insensitive.
     *
     * @param parent Node below which search occurs.
     * @param nodeName Node type.
     * @param attributeName Name of attribute.
     * @param value Value of attribute as Regex.
     * @return Node or null.
     */

    fun findNodesByAttributeValue (parent: Node, nodeName: String, attributeName: String, value: Regex): List<Node>
    {
      return findNodesByAttributeValueInternal(parent, nodeName, attributeName, value)
    }



    /****************************************************************************/
    /**
     * Returns the first node with a given name, or null if not found.
     *
     *
     * IMPORTANT: You can't use this method to find text nodes.
     *
     * @param doc Owning document.
     * @param nodeName Node name.
     * @return Node The selected node.
     */

    fun findNodeByName (doc: Document, nodeName: String): Node?
    {
        return findNodeByName(doc.documentElement, nodeName, true)
    }


    /****************************************************************************/
    /**
     * Returns the first node with a given name under a given node, or null if not
     * found.
     *
     * IMPORTANT: You can't use this method to find text nodes.
     *
     * @param node Owning node.
     * @param nodeName Node name.
     * @param includeRoot True if root note is to be included in search.
     * @return The selected node.
     */

    fun findNodeByName (node: Node, nodeName: String, includeRoot: Boolean): Node?
    {
      val res: Node?
      if (includeRoot && nodeName == getNodeName(node))
        res = node
      else
        try
        {
          val xPathFactory = XPathFactory.newInstance()
          val xPath = xPathFactory.newXPath()
          //val expr = xPath.compile(".//$nodeName[1]")$
          val expr = xPath.compile(".//*[local-name()='$nodeName'][1]")
          res = expr.evaluate(node, XPathConstants.NODE) as Node?
        }
        catch (e: Exception)
        {
          throw StepExceptionWithStackTraceAbandonRun(e)
        }

        return res
    }


    /****************************************************************************/
    /**
     * Returns a list containing all nodes whose names match a given pattern
     * having a given attribute whose value matches a given pattern.
     *
     *
     * IMPORTANT: You can't use this method to find text nodes.
     *
     * @param doc Document.
     * @param nodeNameRegex Regular expression for node name.
     * @param attributeName Name of attribute
     * @param attributeValueRegex Regular expression for attribute value.
     * @return Node The selected nodes.
     */

    fun findNodesByRegexNodeNameAndAttributeValue (doc: Document, nodeNameRegex: String, attributeName: String, attributeValueRegex: String): List<Node>
    {
        return findNodesByRegexNodeNameAndAttributeValue(doc.documentElement, nodeNameRegex, attributeName, attributeValueRegex)
    }


    /****************************************************************************/
    /**
     * Returns a list containing all nodes whose names match a given pattern
     * having a given attribute whose value matches a given pattern.
     *
     *
     * IMPORTANT: You can't use this method to find text nodes.
     *
     * @param parent Node below which we search.
     * @param nodeNameRegex Regular expression for node name.
     * @param attributeName Name of attribute
     * @param attributeValueRegex Regular expression for attribute value.
     * @return The selected nodes.
     */

    fun findNodesByRegexNodeNameAndAttributeValue (parent: Node, nodeNameRegex: String, attributeName: String, attributeValueRegex: String): List<Node>
    {
        return try
        {
            val xpath = makeXPathFactory().newXPath()
            val expr = xpath.compile(".//*[matches(name(), '$nodeNameRegex') and matches(@$attributeName, '$attributeValueRegex')]")
            toListOfNodes(expr.evaluate(parent, XPathConstants.NODESET) as NodeList)
        }
        catch (e: Exception)
        {
            throw StepExceptionWithStackTraceAbandonRun(e)
        }
    }


    /****************************************************************************/
    /**
     * Returns a list containing all nodes with a given name, or null if not
     * found.
     *
     *
     * IMPORTANT: You can't use this method to find text nodes.
     *
     * @param doc Document.
     * @param nodeName Node name.
     * @return List of nodes.
     */

    fun findNodesByName (doc: Document, nodeName: String?): List<Node>
    {
        return toListOfNodes(doc.getElementsByTagName(nodeName))
    }


    /****************************************************************************/
    /**
     * Returns a list containing all nodes with a given name, or null if not
     * found.
     *
     *
     * IMPORTANT: You can't use this method to find text nodes, although if a
     * text node is passed as the first argument, it will at least cope.
     *
     * @param node Parent node.
     * @param nodeName Node name.
     * @param includeParent If true, the parent node is included in the results.
     * @return List of nodes.
     */

    fun findNodesByName (node: Node, nodeName: String, includeParent: Boolean): List<Node>
    {
        if ("#text" == getNodeName(node)) return ArrayList()
        val res = toListOfNodes((node as Element).getElementsByTagName(nodeName)).toMutableList()
        if (includeParent && nodeName == getNodeName(node)) res.add(0, node)
        return res
    }


    /****************************************************************************/
    /**
     * Generates a collection of all the nodes below a given node, depth first.
     *
     * @param startNode Starting node.
     * @return List of nodes.
     */

    fun getAllNodesBelow (startNode: Node): List<Node>
    {
        val res: MutableList<Node> = ArrayList()
        //if ("#document" != getNodeName(startNode)) res.add(startNode)
        getAllNodesBelow(res, startNode)
        return res
    }


    /****************************************************************************/
    /**
     * Returns a node nLevels higher than the present one, or null if we drop off
     * the top of the tree.
     *
     * @param theNode Node of interest.
     * @param nLevels Number of levels to move up.
     * @return Ancestor node.
     */

    fun getAncestor (theNode: Node, nLevels: Int): Node?
    {
        var node: Node? = theNode
        for (i in 0..< nLevels)
        {
            node = getParent(node!!)
            if (null == node) return null
        }

        return node
    }


    /****************************************************************************/
    /**
     * If a node has an ancestor with a given name, returns that ancestor.
     * Otherwise returns null.
     *
     * @param n Node.
     * @param ancestorName Required name.
     * @return Nearest ancestor of the given name, or null.
     */

    fun getAncestorNamed (n: Node, ancestorName: String): Node?
    {
        var parent: Node? = n
        while (true)
        {
            parent = getParent(parent!!)
            if (null == parent || parent is Document) break
            if (getNodeName(parent) == ancestorName) return parent
        }

        return null
    }


    /****************************************************************************/
    /**
     * If a node has an ancestor with a given name and style, returns that
     * ancestor.  Otherwise returns null.
     *
     * @param n Node.
     * @param ancestorName Required name.
     * @param ancestorStyle Required style.
     * @return Nearest ancestor of the given name and style, or null.
     */

    fun getAncestorNamed (n: Node, ancestorName: String, ancestorStyle: String): Node?
    {
        var parent: Node = n
        while (true)
        {
            parent = getParent(parent)!!
            if (parent is Document) break
            if (getNodeName(parent) == ancestorName && hasAttribute(parent, "style") && ancestorStyle == getAttribute(parent, "style")) return parent
        }

        return null
    }


    /****************************************************************************/
    /**
     * If a node has an ancestor with a given name and style, returns that
     * ancestor.  Otherwise returns null.
     *
     * @param n Node.
     * @param ancestorName Required name.
     * @param ancestorStyleRegex Required as regex.
     * @return Nearest ancestor of the given name and style, or null.
     */

    fun getAncestorNamedWithRegexStyle (n: Node, ancestorName: String, ancestorStyleRegex: String): Node?
    {
        var parent: Node? = n
        while (true)
        {
            parent = getParent(parent!!)
            if (null == parent) break
            if (getNodeName(parent) == ancestorName && hasAttribute(parent, "style") && getAttribute(parent, "style")!!.matches(ancestorStyleRegex.toRegex())) return parent
        }

        return null
    }


    /****************************************************************************/
    /**
     * Checks if a given node has an ancestor which satisfies a given condition.
     *
     * @param n Node.
     * @param predicate Predicate
     * @return The ancestor if found, otherwise null.
     */

    fun getAncestorSatisfying (n: Node, predicate: (Node) -> Boolean): Node?
    {
      var parent = n

      while (true)
      {
        parent = parent.parentNode ?: return null
        if (predicate.invoke((parent))) return parent
      }
    }


    /****************************************************************************/
    /**
     * Returns a given attribute value, or null if there is no such attribute.
     *
     * @param node Node.
     * @param attributeName  Name of attribute.
     * @return Attribute value, or null
     */

    fun getAttribute (node: Node, attributeName: String): String?
    {
        if (!node.hasAttributes()) return null
        val n = node.attributes.getNamedItem(attributeName) ?: return null
        return n.nodeValue
    }


    /****************************************************************************/
    /**
     * Returns the attributes of the given node.  (The map may be empty if there
     * are no attributes.)
     *
     * @param node Node.
     * @return  Attributes.
     */

    fun getAttributes (node: Node): Map<String, String>
    {
        val res: MutableMap<String, String> = TreeMap<String, String>(java.lang.String.CASE_INSENSITIVE_ORDER)
        if (!node.hasAttributes()) return res
        val attributeMap: NamedNodeMap = node.attributes
        for (i in 0..< attributeMap.length)
        {
            val n: Node = attributeMap.item(i)
            res[n.nodeName] = n.nodeValue
        }

        return res
    }


    /****************************************************************************/
    /**
     * Given a node, runs over all descendants to any depth accumulating canonical
     * text.
     *
     * @param node Node of interest.
     * @return Text content.
     */

    fun getCanonicalTextContentToAnyDepth (node: Node): String
    {
        var res = ""
        val addWordCount = Consumer { thisNode: Node ->
            if ("#text" == getNodeName(thisNode))
            {
                res += " " + thisNode.textContent
                return@Consumer
            }
            val nodes = toListOfNodes(node.childNodes) // Selects both notes nodes and things which were note but which had errors.
            nodes.forEach { x: Node -> res += " " + getCanonicalTextContentToAnyDepth(x) }
        }

        val nodes = toListOfNodes(node.childNodes) // Selects both notes nodes and things which were note but which had errors.
        nodes.forEach(addWordCount)
        return res.trim()
    }


    /****************************************************************************/
    /**
     * Looks at the children of the parent of a given node, and determines the
     * index within the list of children at which the given node resides.
     *
     * @param n Node of interest.
     * @return Index.
     */

    fun getChildNumber (n: Node): Int
    {
        val p = getParent(n)
        val children = p!!.childNodes
        for (i in 0..< children.length) if (n === children.item(i)) return i
        return -1 // Can't get here, but need to keep compiler happy.
    }


    /****************************************************************************/
    /**
     * Gets the children of a given node as a list of nodes.
     *
     * @param node Node whose children are required.
     * @return List of child nodes.
     */

    fun getChildren (node: Node): List<Node>
    {
        val children = node.childNodes
        val res: MutableList<Node> = ArrayList(children.length)
        for (i in 0..< children.length) res.add(children.item(i))
        return res
    }


    /****************************************************************************/
    /**
     * Returns the first ancestor which a and b have in common.
     *
     * @param a First node.
     * @param b Second node.
     *
     * @return Common ancestor.
     */

    fun getCommonAncestor (a: Node, b: Node): Node
    {
        val parents: MutableSet<Node> = HashSet()
        var p: Node? = a
        while (true)
        {
            p = getParent(p!!)
            if (null == p) break
            parents.add(p)
        }

        p = b
        while (true)
        {
            p = getParent(p!!)
            if (null == p) break
            if (parents.contains(p)) return p
        }

        throw StepExceptionWithStackTraceAbandonRun("Nodes did not have common ancestor")
    }


    /****************************************************************************/
    /**
     * If a node has a descendant with a given name, returns the first such
     * descendant (depth first).  Otherwise returns null.
     *
     * @param theNode Node.
     * @param descendantName Required name.
     * @return First child of the given name, or null.
     */

    fun getDescendantNamed (theNode: Node?, descendantName: String): Node?
    {
        var n = theNode
        val nl = n!!.childNodes
        for (i in 0..< nl.length) {
            val child = nl.item(i)
            if (descendantName == child.nodeName) return nl.item(i)
            n = getDescendantNamed(child, descendantName)
            if (null != n) return n
        }
        return null
    }


    /****************************************************************************/
    /**
     * Returns the DOM document corresponding to a given input file.
     *
     * @param inputFilePath Input file path.
     * @param retainComments By default, comments are removed.  This retains them.
     * @return DOM document.
     * @throws Exception Any exception.
     */

    fun getDocument (inputFilePath: String, retainComments: Boolean = false): Document = getDocumentFromText(File(inputFilePath).readText(), retainComments)


    /****************************************************************************/
    /**
     * Returns the DOM document corresponding to a given input file.
     *
     * @param text XML text.
     * @param retainComments By default, comments are removed.  This retains them.
     * @return DOM document.
     * @throws Exception Any exception.
     */

    fun getDocumentFromText (text: String, retainComments: Boolean = false): Document
    {
      /************************************************************************/
      /* There is a slightly awkward decision as to whether the factory should
         be made namespace aware below.  Parsing USX is fine because it has no
         namespace setting.  But parsing OSIS (which recent requirement changes
         have made necessary) is a problem, because according to the
         documentation you do need namespace details there.

         I have decided to retain namespace awareness.  However this means you
         have to be careful within the implementation of the various find*
         methods here to use local-name() to pick up the node names.

         (For some reason, findNodesByName doesn't seem to be affected by this
         issue.) */

        System.setProperty("javax.xml.xpath.XPathFactory:" + NamespaceConstant.OBJECT_MODEL_SAXON, "net.sf.saxon.xpath.XPathFactoryImpl")
        val factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false // See comments above.
        factory.isIgnoringComments = !retainComments
        factory.isIgnoringElementContentWhitespace = true
        factory.isCoalescing = true
        val builder: DocumentBuilder = factory.newDocumentBuilder()
        val inputData = ByteArrayInputStream(text.replace("\u00a0", "&#160;").toByteArray()) // Unicode non-breaking space.
        //Dbg.outputText(text.replace("\u00a0", "&#160;"))
        val doc =  builder.parse(inputData)
        doc.normalizeDocument()
        return doc
    }


    /****************************************************************************/
    /**
     * Returns a node which is an ancestor of child and an immediate child of
     * ancestor.
     *
     * @param ancestor Ancestor.
     * @param child Child.
     *
     * @return Ancestor of child which is immediate child of Ancestor.  If
     * ancestor is itself the immediate parent of child, then null.
     */

    fun getIntermediary (ancestor: Node, child: Node): Node?
    {
        var p: Node? = child
        while (null != p)
        {
            val parent = getParent(p)
            if (parent === ancestor) break
            p = parent
        }
        return if (p === child) null else p
    }


    /****************************************************************************/
    /**
     * Returns the lineage (ie the ancestor line) for a given node, not including
     * the node itself.
     *
     * @param theNode Node of interest.
     * @return Lineage (null if node is root).
     */

    fun getLineage (theNode: Node): List<Node>
    {
        var node: Node? = theNode
        val res: MutableList<Node> = ArrayList()
        while (true)
        {
            node = getParent(node!!)
            if (null == node) break
            res.add(node)
        }

        return res
    }


    /****************************************************************************/
    /**
     * Returns the next sibling of a given node, or null if there are no more.
     *
     * @param node Node whose sibling is required.
     * @return Sibling, or null.
     */

    fun getNextSibling (node: Node): Node?
    {
        return node.nextSibling
    }


    /****************************************************************************/
    /**
     * Returns the name of the node.
     *
     * @param node Node of interest.
     * @return Node name.
     */

    fun getNodeName (node: Node): String
    {
        return node.nodeName
    }


    /****************************************************************************/
    /**
     * Gets a list of nodes starting with node a, and running until a node
     * for which 'test' returns true.  The list is in pre-order.  It returns all
     * nodes from the one encountered after node 'a' up to but not including the
     * first node to satisfy 'test', but excludes any nodes which satisfy the
     * 'exclude' test (and also excludes their descendants).
     *
     * @param a Node from which to start.
     * @param test Test used to determine ending node.
     * @param exclude If true, a given node and its descendants are excluded from
     * the list.  If supplied as null, all nodes are included in
     * the list.
     *
     * @return List of nodes.
     */

    fun getNodesBetweenExcludingStartAndEnd (a: Node, test: Function<Node, Boolean>, exclude: Function<Node, Boolean>): List<Node>
    {
        return getNodesBetween(a, test, exclude, includeStart = false, includeEnd = false)
    }


    /****************************************************************************/
    /**
    * Gets the XML content below a given node as a string.  Originally I wanted
    * something which would simply give back the text content.  Be aware,
    * therefore, that this does something rather different -- it gives back the
    * entire subtree, including tag names etc, as a text string.  In particular,
    * unless you use includeOwningTag to prevent it, it will include the
    * owning tag -- so you'll get something like '<myTag>some content</myTag>'.
    *
    * @param node The node whose content is of interest.
    * @param includeOwningTag Determines whether a representation of the owning tag appears in the output.
    * @return Textual representation of content.
    */

    fun getNodeContentAsString (node: Node, includeOwningTag: Boolean = false): String
    {
      val  sw = StringWriter()
      val  result = StreamResult(sw)
      val  source = DOMSource(node)
      makeTransformer().transform(source, result)
      var res = sw.toString()

      if (!includeOwningTag)
      {
        res = res.substring(res.indexOf(">") + 1)
        res = res.substring(0, res.lastIndexOf("<"))
      }

      return res
    }


    /****************************************************************************/
    /**
     * Gets a list of nodes starting with node a, and running until a node
     * for which 'test' returns true.  The list is in pre-order.  It includes
     * node 'a' and all nodes up to and including the first to satisfy 'test',
     * except that nodes for which 'exclude' returns true are not added to the
     * list (and nor are their descendants).
     *
     * @param a Node from which to start.
     * @param test Test used to determine ending node.
     * @param exclude If true, a given node and its descendants are excluded from
     * the list.  If supplied as null, all nodes are included in
     * the list.
     *
     * @return List of nodes.
     */

    fun getNodesBetweenIncludingStartAndEnd (a: Node, test: Function<Node, Boolean>, exclude: Function<Node, Boolean>): List<Node>
    {
        return getNodesBetween(a, test, exclude, includeStart = true, includeEnd = true)
    }


    /****************************************************************************/
    /**
     * Returns the parent of a given node.
     *
     * @param child Child.
     * @return Parent.
     */

    fun getParent(child: Node): Node?
    {
        return child.parentNode
    }


    /****************************************************************************/
    /**
     * Returns the previous sibling of a given node, or null if there is none.
     *
     * @param node Node whose sibling is required.
     * @return Sibling, or null.
     */

    fun getPreviousSibling (node: Node): Node?
    {
        return node.previousSibling
    }


    /****************************************************************************/
    /**
     * Returns the previous sibling of a given node which does not satisfy a
     * given condition, or null.
     *
     * @param node Node of interest.
     * @return Sibling, or null.
     */

    fun getPreviousSiblingNotSatisfying (node: Node, predicate: (Node) -> Boolean): Node?
    {
      return getPreviousSiblingSatisfying(node) { x -> !predicate(x) }
    }


    /****************************************************************************/
    /**
     * Returns the previous sibling of a given node which satisfies a given
     * condition, or null.
     *
     * @param node Node of interest.
     * @return Sibling, or null.
     */

    fun getPreviousSiblingSatisfying (node: Node, predicate: (Node) -> Boolean): Node?
    {
      val siblings = getSiblings(node)
      var ix = getChildNumber(node)
      while (--ix >= 0)
        if (predicate(siblings[ix])) return siblings[ix]

      return null
    }


    /****************************************************************************/
    /**
     * Returns the root node.
     *
     * @param doc Owning document.
     * @return Root node.
     */

    fun getRootNode (doc: Document): Node
    {
        return doc.documentElement as Node
    }


    /****************************************************************************/
    /**
     * Gets the siblings of a particular node.
     *
     * @param node Node of interest.
     * @return Node list (empty if no siblings).
     */

    fun getSiblings (node: Node): List<Node>
    {
        val parent = getParent(node) ?: return listOf()
        return toListOfNodes(parent.childNodes)
    }


    /****************************************************************************/
    /**
     * Gets all younger siblings of a given node.
     *
     * @param start Start node.
     * @param includeStart If true, the start node is included in the result.
     * @return List of siblings.
     */

    fun getSiblingsStartingAt (start: Node, includeStart: Boolean): List<Node>
    {
      val allSiblings = getSiblings(start)
      val ix = allSiblings.indexOf(start)
      if (1 == allSiblings.size && !includeStart) return listOf()
      return allSiblings.subList(if (includeStart) ix else ix + 1, allSiblings.size)
    }


    /****************************************************************************/
    /**
     * Checks if a given node has an ancestor with a given name.
     *
     * @param n Node.
     * @param ancestorName Required name.
     * @return True if the node has an ancestor of the given name.
     */

    fun hasAncestorNamed (n: Node, ancestorName: String): Boolean
    {
        val ancestor = getAncestorNamed(n, ancestorName)
        return null != ancestor
    }


    /****************************************************************************/
    /**
     * Checks if a given node has an ancestor with a given name and style.
     *
     * @param n Node.
     * @param ancestorName Required name.
     * @param ancestorStyle Required style.
     * @return True if the node has an ancestor of the given name and style.
     */

    fun hasAncestorNamed (n: Node, ancestorName: String, ancestorStyle: String): Boolean
    {
        val ancestor = getAncestorNamed(n, ancestorName, ancestorStyle)
        return null != ancestor
    }


    /****************************************************************************/
    /**
    * Checks if a given node is an ancestor of another node.
    *
    * @param descendant The lower level node.
    * @param putativeAncestor The node we believe might be an ancestor.
    * @return True if the descendant-ancestor relationship holds.
    */

    fun hasAsAncestor (descendant: Node, putativeAncestor: Node): Boolean
    {
      if (descendant === putativeAncestor) return false

      var myDescendant = descendant

      while (true)
      {
        myDescendant = myDescendant.parentNode ?: return false
        if (myDescendant === putativeAncestor) return true
      }
    }


    /****************************************************************************/
    /**
     * Checks if a given node has a given attribute.
     *
     * @param node Node.
     * @param attributeName  Name of attribute.
     * @return True if node has the given attribute.
     */

    fun hasAttribute (node: Node, attributeName: String): Boolean
    {
        return null != getAttribute(node, attributeName)
    }


    /****************************************************************************/
    /**
     * Checks if a given node has a given attribute with a value matching a given
     * pattern.
     *
     * @param node Node.
     * @param attributeName  Name of attribute.
     * @param attributeValuePattern Pattern for attribute value.
     * @return True if node has the given attribute.
     */

    fun hasAttribute (node: Node, attributeName: String, attributeValuePattern: String): Boolean
    {
        return hasAttribute(node, attributeName) && getAttribute(node, attributeName)!!.matches(attributeValuePattern.toRegex())
    }


    /****************************************************************************/
    /**
     * Determines if a given parent has at least one descendant with a given name.
     *
     * @param parent Parent node.
     * @param nodeName Name of descendant.
     * @return True if the parent has at least one descendant with the given name.
     */

    fun hasDescendantNamed (parent: Node, nodeName: String): Boolean
    {
        return null != findNodeByName(parent, nodeName, false)
    }


    /****************************************************************************/
    /**
    * Determines whether a given node has any non-whitespace children.
    *
    * @return True if there are any non-whitespace children.
    */

    fun hasNonWhitespaceChildren (node: Node): Boolean
    {
      return getChildren(node).any { !isWhitespace(it) }
    }


    /****************************************************************************/
    /**
     * Inserts a given node as the first or only child of a given node.
     *
     * @param parent Parent node.
     * @param newChild  Node to be inserted.
     */

    fun insertAsFirstChild (parent: Node, newChild: Node)
    {
        if (parent.hasChildNodes()) insertNodeBefore(parent.firstChild, newChild) else parent.appendChild(newChild)
    }


    /****************************************************************************/
    /**
     * Inserts a given node as the last or only child of a given node.
     *
     * @param parent Parent node.
     * @param newChild  Node to be inserted.
     */

    fun insertAsLastChild (parent: Node, newChild: Node)
    {
        if (parent.hasChildNodes()) insertNodeAfter(parent.lastChild, newChild) else parent.appendChild(newChild)
    }


    /****************************************************************************/
    /**
     * Inserts a node following another node and as a sibling of it.
     *
     * @param target Node before which new node is to be inserted.
     * @param newNode New node.
     */

    fun insertNodeAfter (target: Node, newNode: Node)
    {
        val nextSibling = getNextSibling(target)
        val parent = getParent(target)!!
        if (null == nextSibling) parent.appendChild(newNode) else insertNodeBefore(nextSibling, newNode)
    }


    /****************************************************************************/
    /**
     * Inserts a node prior to another node and as a sibling of it.
     *
     * @param target Node before which new node is to be inserted.
     * @param newNode New node.
     */

    fun insertNodeBefore (target: Node, newNode: Node)
    {
        getParent(target)!!.insertBefore(newNode, target)
    }


    /****************************************************************************/
    /**
     * Inserts a list of nodes following another node and as siblings of it.
     *
     * @param target Node before which new nodes are to be inserted.
     * @param newNodes List of new nodes.
     */

    fun insertNodesAfter (target: Node, newNodes: List<Node>)
    {
        for (i in newNodes.indices.reversed()) insertNodeAfter(target, newNodes[i])
    }


    /****************************************************************************/
    /**
     * Inserts a list of nodes prior to another node and as siblings of it.
     *
     * @param target Node before which new nodes are to be inserted.
     * @param newNodes List of new nodes.
     */

    fun insertNodesBefore(target: Node, newNodes: List<Node>)
    {
        for (i in newNodes.indices) insertNodeBefore(target, newNodes[i])
    }


    /****************************************************************************/
    /**
     * Returns true if 'ancestor' is the ancestor of 'descendant'.
     *
     * @param ancestor Putative ancestor node.
     * @param descendant Descendant node.
     * @return True if 'ancestor' is the ancestor of 'descendant'.
     */

    fun isAncestorOf (ancestor: Node, descendant: Node): Boolean
    {
        var n: Node? = descendant
        while (true)
        {
            n = getParent(n!!)
            if (null == n) break
            if (n is Document) break
            if (n === ancestor) return true
        }

        return false
    }


    /****************************************************************************/
    /**
     * Returns true if 'child' is the child of 'parent'.
     *
     * @param parent Parent node.
     * @param child Putative child node.
     * @return True if 'child' is the child of 'parent'
     */

    fun isChildOf (child: Node, parent: Node): Boolean
    {
        return isParentOf(parent, child)
    }


    /****************************************************************************/
    /**
     * Checks if a given node is a comment node.
     *
     * @param node Node to be tested.
     * @return True if comment node.
     */

    fun isCommentNode (node: Node): Boolean
    {
        return "#comment" == getNodeName(node)
    }


    /****************************************************************************/
    /**
     * Returns true if 'descendant' is the descendant of 'ancestor'.
     *
     * @param descendant Descendant node.
     * @param ancestor Putative ancestor node.
     * @return True if 'descendant' is the parent of 'ancestor'.
     */

    fun isDescendantOf (descendant: Node, ancestor: Node): Boolean
    {
        return isAncestorOf(ancestor, descendant)
    }


     /****************************************************************************/
    /**
     * Determines whether a given node is the first non-blank child of another.
     *
     * @param parent Parent node.
     * @param child Child node.
     * @return True if first non-blank child; false if not a child or if not
     * first non-blank child.
     */

    fun isFirstNonBlankChildOf (parent: Node, child: Node): Boolean
    {
        val children = parent.childNodes
        for (i in 0..< children.length)
        {
            val n = children.item(i)
            if (n === child) return true
            if (!isWhitespace(n)) return false
        }

        return false
    }


    /****************************************************************************/
    /**
     * Determines whether a given node is the first non-blank descendant of
     * another.
     *
     * @param ancestor Ancestor node.
     * @param theChild Child node.
     * @return True if, working up through all the levels above child up to
     * ancestor, each node is the first non-blank child of its parent.
     */

    fun isFirstNonBlankDescendantOf (ancestor: Node, theChild: Node): Boolean
    {
        var child = theChild
        var parent: Node?
        while (true)
        {
            parent = child.parentNode
            if (null == parent) break
            if (!isFirstNonBlankChildOf(parent, child)) return false
            if (parent === ancestor) return true
            child = parent
        }

        return false
    }


    /****************************************************************************/
    /**
     * Determines whether a given node is the last non-blank child of another.
     *
     * @param parent Parent node.
     * @param child Child node.
     * @return True if last non-blank child; false if not a child or if not
     * last non-blank child.
     */

    fun isLastNonBlankChildOf (parent: Node, child: Node): Boolean
    {
        val children = parent.childNodes
        for (i in children.length - 1 downTo 0)
        {
            val n = children.item(i)
            if (n === child) return true
            if (!isWhitespace(n)) return false
        }

        return false
    }


    /****************************************************************************/
    /**
     * Checks whether a given node immediately follows another.
     *
     * @param first  First node.
     * @param second Second node (the one which should be second).
     * @param ignoreBlanks If true, blanks are allowed between the two nodes.
     * @return True if the second node succeeds the first.
     */

    fun isNextSiblingOf (first: Node, second: Node, ignoreBlanks: Boolean): Boolean
    {
        val p = getParent(first)
        if (p !== getParent(second)) return false
        var foundFirst = false
        var foundSecond = false
        val children = p!!.childNodes
        for (i in 0..< children.length)
        {
            val child = children.item(i)

            if (!foundFirst)
            {
                foundFirst = child === first
                continue
            }

            if (child === second)
            {
                foundSecond = true
                break
            }

            if (!isWhitespace(child)) return false
            if (!ignoreBlanks) return false
        }

        return foundFirst && foundSecond
    }


    /****************************************************************************/
    /**
     * Returns true if 'parent' is the parent of 'child'.
     *
     * @param parent Putative parent node.
     * @param child Child node.
     * @return True if 'parent' is the parent of 'child'.
     */

    fun isParentOf (parent: Node, child: Node) = getParent(child) === parent


     /****************************************************************************/
    /**
     * Returns true if 'parent' is the parent of 'child'.
     *
     * @param a First node.
     * @param b Second node.
     * @return True if 'parent' is the parent of 'child'.
     */

    fun isSiblingOf (a: Node, b: Node) = getParent(a) === getParent(b)


    /****************************************************************************/
    /**
     * Checks if a given node is a text node.
     *
     * @param node Node to be tested.
     * @return True if text node.
     */

    fun isTextNode (node: Node) = "#text" == getNodeName(node)


    /****************************************************************************/
    /**
     * Checks if a node is textual and comprises whitespace only.
     *
     * @param node Node.
     * @return True if whitespace text node.
     */

    fun isWhitespace (node: Node): Boolean
    {
        if ("#text" != getNodeName(node)) return false
        val contents = node.textContent.replace("\\p{Z}".toRegex(), "").replace("\\s".toRegex(), "") // Remove separators like spaces etc.
        return contents.isEmpty()
    }


    /****************************************************************************/
    /**
     * Takes a parent node, and starting at the front of its children, applies fn
     * to each until a node is encountered which does not satisfy 'test'.
     *
     * @param parent: Parent node.
     * @param fn: Function to be applied to selected nodes.
     * @param test: Function which determines which nodes to process.  Default is to
     *          run over all nodes.
     * @param giveUpOnFailedTest: If true, gives up as soon as 'test' returns false.
     * @return Pair comprising Nodes which were passed to fn, and the node at which
     *         processing ended (null if all nodes were processed)
     */

    fun iterateOverLeadingChildrenSatisfyingCondition (parent: Node, fn: (Node) -> Any, test: ((Node) -> Boolean)?, giveUpOnFailedTest: Boolean = true):
      Pair<List<Node>, Node?>
    {
      return iterateOverLeadingNodesSatisfyingCondition(getChildren(parent), fn, test, giveUpOnFailedTest)
    }


   /****************************************************************************/
   /**
    * Takes a list of nodes, and starting at the front of the list, applies fn
    * to each until a node is encountered which does not satisfy 'test'.
    *
    * @param nodes: List of nodes to be processed.
    * @param fn: Function to be applied to selected nodes.
    * @param test: Function which determines which nodes to process.  Default is to
    *          run over all nodes.
    * @param giveUpOnFailedTest: If true, gives up as soon as 'test' returns false.
    * @return Pair comprising Nodes which were passed to fn, and the node at which
    *         processing ended (null if all nodes were processed)
    */

   fun iterateOverLeadingNodesSatisfyingCondition (nodes: List<Node>, fn: (Node) -> Any, test: ((Node) -> Boolean)?, giveUpOnFailedTest: Boolean = true):
     Pair<List<Node>, Node?>
   {
     val returnedNodeList: MutableList<Node> = mutableListOf()
     var failedNode: Node? = null

     for (n in nodes)
     {
       if (null != test && giveUpOnFailedTest && !test(n))
       {
         failedNode = n
         break
       }

       returnedNodeList.add(n)
       fn(n)
     }

     return Pair(returnedNodeList, failedNode)
   }


   /****************************************************************************/
   /**
    * Takes a parent node, and starting at the front of its children, applies fn
    * to each until a node is encountered which does not satisfy 'test'.
    *
    * @param start: First node.
    * @param fn: Function to be applied to selected nodes.
    * @param test: Function which determines which nodes to process.  Default is to
    *          run over all nodes.
    * @param giveUpOnFailedTest: If true, gives up as soon as 'test' returns false.
    * @param includeStart: Determines whether the first node is included.
    * @return Pair comprising Nodes which were passed to fn, and the node at which
    *         processing ended (null if all nodes were processed)
    */

   fun iterateOverYoungerSiblingsSatisfyingCondition (start: Node, fn: (Node) -> Any, test: ((Node) -> Boolean)?, giveUpOnFailedTest: Boolean = true, includeStart: Boolean = true):
     Pair<List<Node>, Node?>
   {
     val returnedNodeList: MutableList<Node> = mutableListOf()
     var failedNode: Node? = null
     var n = if (includeStart) start else start.nextSibling

     while (null != n)
     {
       if (null != test && giveUpOnFailedTest && !test(n))
       {
         failedNode = n
         break
       }

       returnedNodeList.add(n)
       fn(n)

       n = n.nextSibling
     }

      return Pair(returnedNodeList, failedNode)
    }


    /****************************************************************************/
    /**
    * Makes a mutable map given a list of nodes.  keyFn takes a node and returns
    * a key value for it.
    *
    * @param keyFn Key generator.
    * @return Mutable map associating key values with nodes.
    */

    fun <T> List<Node>.makeNodeMap (keyFn: (Node) -> T): MutableMap<T, Node> = this.associateBy { keyFn(it) } .toMutableMap()


    /****************************************************************************/
    /**
    * Checks if two nodes are adjacent but for whitespace.
    *
    * @param firstNode Must be guaranteed to be the earlier of the two nodes as
    *   would appear if we applied getNodesInTree to the parent node.
    *
    * @param lastNode Last node.
    *
    * @param ignoreWhitespace What it says on the tin -- nodes will still be
    *   regarded as adjacent even if separated by whitespace.
    *
    * @return True if nodes are adjacent.
    */

    fun nodesAreAdjacent (firstNode: Node, lastNode: Node, ignoreWhitespace: Boolean = true): Boolean
    {
      val parent = firstNode.parentNode
      if (lastNode.parentNode !== parent)
        return false

      var checking = false

      for (sibling in getChildren(parent))
      {
        if (sibling === firstNode)
          checking = true
        else if (sibling == lastNode)
          return true
        else if (checking)
        {
          if (isWhitespace(sibling) && ignoreWhitespace)
            continue
          else
            return false
        }
      } // for

      throw StepExceptionWithStackTraceAbandonRun("nodesAreAdjacent: Unexpected case.")
    }


    /****************************************************************************/
    /** Converts a DOM to a string representation.
    *
    * @param doc
    * @return String representation.
    */

    fun outputDomAsString (doc: Document): String
    {
      val transformerFactory = TransformerFactory.newInstance()
      val transformer = transformerFactory.newTransformer()

      // Optional: Set output properties
      transformer.setOutputProperty(OutputKeys.INDENT, "yes")
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")

      val writer = StringWriter()
      val result = StreamResult(writer)
      val source = DOMSource(doc)

      transformer.transform(source, result)

      return writer.toString()
    }


    /****************************************************************************/
    /**
     * Outputs the DOM as XML, to a file or to System.out.
     *
     * @param doc Document.
     * @param filePath File or null if you want output to System.out.
     * @param theComment Any comment which you wish to have at the top of the file,
     *   or null.
     * @param textPatchUpFn Makes any changes to the text.  For example, I've
     *   found it necessary to replace &lt; and &gt; to ^lt; and ^gt; in some
     *   places because I've been processing USX which contains these entities,
     *   and left as such in the input, they mess things up.  But having done that,
     *   I need to change them back again here.
     */

    fun outputDomAsXml (doc: Document, filePath: String?, theComment: String?, textPatchUpFn: ((String) -> String)? = null)
    {
        try
        {
            doc.normalize()
            val transformer: Transformer = TransformerFactory.newInstance().newTransformer()
            transformer.setOutputProperty(OutputKeys.INDENT, "no")
            val result = StreamResult(StringWriter())
            val source = DOMSource(doc)
            transformer.transform(source, result)
            var xmlOutput = result.writer.toString().replace("\r", "")
            if (null != textPatchUpFn) xmlOutput = textPatchUpFn(xmlOutput)
            val ix = xmlOutput.indexOf(">") + 1
            val firstBit = xmlOutput.substring(0, ix)
            val comment = if (null == theComment) "" else "<!-- $theComment -->\n"
            val secondBit = xmlOutput.substring(ix)

            if (null == filePath)
            {
                println(firstBit)
                println("\n")
                println(comment)
                println(secondBit)
            }
            else
                Files.newBufferedWriter(Paths.get(filePath)).use { fOut ->
                  fOut.write(firstBit)
                  fOut.write("\n")
                  fOut.write(comment)
                  fOut.write(secondBit)
                  fOut.write("")
              }
        }
        catch (e: Exception)
        {
            throw StepExceptionWithStackTraceAbandonRun(e)
        }
    }


    /****************************************************************************/
    /**
     * Promotes all the children of a given node to be siblings of the original
     * node (immediately following it).  This has the effect of turning the
     * original node into a self-closing node, whilst retaining the detail which
     * originally resided below it.
     *
     * @param parent Parent node.
     */

    fun promoteChildren (parent: Node)
    {
        val doc = parent.ownerDocument
        val children = parent.childNodes
        for (j in children.length - 1 downTo 0)
        {
            val child = children.item(j)
            val newNode = createNode(doc, child, true)
            insertNodeAfter(parent, newNode)
            deleteNode(child)
        }
    }


    /****************************************************************************/
    /**
     * Promotes all the children of each node in a list to be siblings of the
     * original node (immediately following it).  This has the effect of turning
     * the original nodes into self-closing nodes, whilst retaining the detail
     * which originally resided below them.
     *
     * @param parents List of parent nodes.
     */

    fun promoteChildren (parents: List<Node>)
    {
       parents.forEach { promoteChildren(it) }
    }


    /****************************************************************************/
    /**
     * Takes a list of nodes returns a list containing only those nodes which do
     * not have parents in the list.
     *
     * @param nl Node list.
     * @return Pruned node list.
     */

    fun pruneToTopLevelOnly (nl: List<Node>): List<Node>
    {
        val parentNodes: MutableMap<Node, Boolean> = IdentityHashMap()
        nl.stream().forEach { x: Node -> parentNodes[x] = true }
        return nl.filter { x: Node -> !parentNodes.containsKey(x.parentNode) }
    }


    /****************************************************************************/
    /**
     * Replaces one node in the tree with another (which may have substructure
     * below it).
     *
     * @param nodeToBeReplaced Node to be replaced.
     * @param replacement Replacement node.
     */

    fun replaceNode (nodeToBeReplaced: Node, replacement: Node)
    {
        getParent(nodeToBeReplaced)!!.replaceChild(replacement, nodeToBeReplaced)
    }


    /****************************************************************************/
    /**
     * Creates a new attribute or changes the value of an existing one.
     *
     * @param node Node.
     * @param attributeName Attribute name.
     * @param attributeValue Value.  (If null, attribute is deleted.)
     */

    fun setAttribute (node: Node, attributeName: String, attributeValue: String?)
    {
      if (null == attributeValue)
        deleteAttribute(node, attributeName)
      else
        (node as Element).setAttribute(attributeName, attributeValue)
    }


    /****************************************************************************/
    /**
     * Adds a collection of attributes to a node.
     *
     * @param node Node.
     * @param attributes Attribute list.
     */

    fun setAttributes (node: Node, attributes: Map<String, String>)
    {
        attributes.keys.forEach { setAttribute(node, it, attributes[it]!!) }
    }


    /****************************************************************************/
    /**
     * Changes the name of a node.
     *
     * @param node Node.
     * @param name New name.
     */

    fun setNodeName (node: Node, name: String)
    {
        val doc = node.ownerDocument
        doc.renameNode(node, null, name)
    }


    /****************************************************************************/
    /**
    * Splits the parent of a node at the node itself, so that the node is
    * promoted.  Any nodes within the parent and prior to the target node go into
    * one copy of the parent node; any nodes within the parent and after the
    * target node go into another copy.
    *
    * @param splitAt Node at which to split.
    */

    fun splitParentAtNode (splitAt:Node)
    {
      /************************************************************************/
      val parent = splitAt.parentNode
      val pre  = cloneNode(parent.ownerDocument, parent, false)
      val post = cloneNode(parent.ownerDocument, parent, false)
      var hitTarget = false



      /************************************************************************/
      getChildren(parent).forEach {
        deleteNode(it)
        if (it === splitAt)
          hitTarget = true
        else if (hitTarget)
          post.appendChild(it)
        else
          pre.appendChild(it)
      }



      /************************************************************************/
      if (pre.hasChildNodes())
        insertNodeBefore(parent, pre)

      insertNodeBefore(parent, splitAt)

      if (post.hasChildNodes())
        insertNodeBefore(parent, post)

      deleteNode(parent)
   }


    /****************************************************************************/
    /**
     * Generates a printable representation of a node and its attributes.
     *
     * @param node Node.
     * @return String.
     */

    fun toString (node: Node): String
    {
        val nodeName = node.nodeName
        if ("#text" == nodeName) return "#text: " + node.textContent else if ("#comment" == nodeName) return "#comment: " + node.textContent

        var res = "$nodeName "
        var attributes = ""

        if (node.hasAttributes())
        {
            val attribs = getAttributes(node)
            for (k in attribs.keys) attributes += k + "='" + attribs[k] + "' "
        }

        if (attributes.isNotEmpty()) attributes = attributes.substring(0, attributes.length -1)
        res += attributes
        return "<$res>"
    }





    /****************************************************************************/
    /****************************************************************************/
    /**                                                                         */
    /**                                Private                                  */
    /**                                                                         */
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    /* Collects all the descendants of a given node. */

    private fun collectDescendants (map: MutableMap<Node, Void?>, node: Node)
    {
       map[node] = null
       val children = node.childNodes
       for (i in 0..< children.length) collectDescendants(map, children.item(i))
   }


  /****************************************************************************/
  /* Takes an XSLT fragment and adds the necessary bits to turn it into the
     real thing. */

  private fun expandBasicStylesheet (document: Document, basicStylesheet: String): String
  {
    val defaultNamespace = getAttribute(document.documentElement, "xmlns")
    val defaultNamespaceSetting = if (null == defaultNamespace) "" else "xpath-default-namespace='$defaultNamespace'"

    val otherNameSpaces = getAttributes(document.documentElement)
      .filterKeys { it.startsWith("xmlns") && "xmlns" != it }
      .map { (name, value) -> "$name='$value'" }
      .joinToString(" ")

    val stylesheet = """
      <xsl:stylesheet version='2.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform' $defaultNamespaceSetting $otherNameSpaces>

        <!-- Identity template to copy all nodes and attributes. -->
        <xsl:template match='@*|node()'>
          <xsl:copy>
            <xsl:apply-templates select='@*|node()'/>
          </xsl:copy>
        </xsl:template>

        $basicStylesheet

      </xsl:stylesheet>
    """

    //Dbg.outputText(stylesheet)

    return stylesheet
  }

    /****************************************************************************/
    private fun findNodeByAttributeValueInternal (parent: Node, nodeName: String, attributeName: String, value: String): Node?
    {
      if ('*' in value || '+' in value) throw StepExceptionWithStackTraceAbandonRun("Calling findNodeByAttributeValue with string which contains wildcards.")
      return try
      {
        val lcValue = value.lowercase()
        val xpath = makeXPathFactory().newXPath()
        val expr = xpath.compile(".//*[local-name()='$nodeName' and lower-case(@$attributeName) = '$lcValue']") //*[local-name()='$nodeName'][@$attributeName and matches(@$attributeName, '$value', 'i')]")
        expr.evaluate(parent, XPathConstants.NODE) as Node
      }
      catch (e: Exception)
      {
        throw StepExceptionWithStackTraceAbandonRun(e)
      }
    }


    /****************************************************************************/
    private fun findNodeByAttributeValueInternal (parent: Node, nodeName: String, attributeName: String, value: Regex): Node?
    {
      return try
      {
        val xpath = makeXPathFactory().newXPath()
        val expr = xpath.compile(".//*[local-name()='$nodeName'][@$attributeName and matches(@$attributeName, '$value', 'i')]")
        expr.evaluate(parent, XPathConstants.NODE) as Node
      }
      catch (e: Exception)
      {
        throw StepExceptionWithStackTraceAbandonRun(e)
      }
    }


    /****************************************************************************/
    private fun findNodesByAttributeValueInternal (parent: Node, nodeName: String, attributeName: String, value: String): List<Node>
    {
      if ('*' in value || '+' in value) throw StepExceptionWithStackTraceAbandonRun("Calling findNodesByAttributeValue with string which contains wildcards.")
      return try
      {
        val lcValue = value.lowercase()
        val xpath = makeXPathFactory().newXPath()
        val expr = xpath.compile(".//*[local-name()='$nodeName' and lower-case(@$attributeName) = '$lcValue']")
        toListOfNodes(expr.evaluate(parent, XPathConstants.NODESET) as NodeList)
      }
      catch (e: Exception)
      {
        throw StepExceptionWithStackTraceAbandonRun(e)
      }
    }


    /****************************************************************************/
    private fun findNodesByAttributeValueInternal (parent: Node, nodeName: String, attributeName: String, value: Regex): List<Node>
    {
      return try
      {
        val xpath = makeXPathFactory().newXPath()
        val expr = xpath.compile(".//*[local-name()='$nodeName'][@$attributeName and matches(@$attributeName, '$value', 'i')]")
        toListOfNodes(expr.evaluate(parent, XPathConstants.NODESET) as NodeList)
      }
      catch (e: Exception)
      {
        throw StepExceptionWithStackTraceAbandonRun(e)
      }
    }


   /****************************************************************************/
   private fun getAllNodesBelow(res: MutableList<Node>, startNode: Node) {
        val children = startNode.childNodes
        for (i in 0..< children.length) {
            val n = children.item(i)
            res.add(n)
            getAllNodesBelow(res, n)
        }
    }

    /****************************************************************************/
    /**
     * Converts a NodeList structure to List&lt;Node&gt;>.
     *
     * @param nl Node list to be converted.
     * @return Revised structure.
     */

    private fun toListOfNodes (nl: NodeList): List<Node>
    {
        val res: MutableList<Node> = ArrayList(nl.length)
        for (i in 0..< nl.length) res.add(nl.item(i))
        return res
    }





    /****************************************************************************/
    /****************************************************************************/
    /**                                                                         */
    /**                               Debugging                                 */
    /**                                                                         */
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    /**
     * Outputs a parse tree to Dbg.
     *
     * @param root Node from which the output should start.
     */

    fun printTree(root: Node)
    {
        try
        {
            printTree(null, root, null, null)
        }
        catch (_: Exception)
        {
        }
    }


    /****************************************************************************/
    /**
     * Outputs a parse tree either to a given file (if filePath is non-null), or
     * via Dbg.
     *
     * @param filePath Path to output file, or null, in which case Dbg.d is used.
     * @param root Node from which the output should start.
     * @throws IOException Any IO error.
     */

    @JvmOverloads
    @Throws(IOException::class)
    fun printTree (filePath: String?, root: Node, attributeName: String? = null, attributeValue: String? = null)
    {
        val fOut = if (null == filePath) null else Files.newBufferedWriter(Paths.get(filePath))
        printTree(root, attributeName, attributeValue, fOut)
    }


    /****************************************************************************/
    /**
     * Outputs a parse tree via Dbg.  If attributeName is non-null, output stops
     * with the first node which has the given value for the given attribute.
     * (The tree below the node is included, however.)
     *
     * @param root Node from which the output should start.
     * @param attributeName Null or name of an attribute.
     * @param attributeValue Null or value of an attribute.
     * @throws IOException Any IO error.
     */

    @Throws(IOException::class)
    private fun printTree (root: Node, attributeName: String?, attributeValue: String?, fOut: BufferedWriter?)
    {
      printParseTreeInternalWrapper(root, attributeName, attributeValue, fOut)
    }


    /****************************************************************************/
    private fun getNodesBetween (a: Node, test: Function<Node, Boolean>, excludeFn: Function<Node, Boolean>?, includeStart: Boolean, includeEnd: Boolean): List<Node>
    {
        /****************************************************************************/
        var exclude: Function<Node, Boolean>? = excludeFn
        if (null == exclude) exclude = Function { _: Node? -> false }
        val myExclude: Function<Node, Boolean> = exclude



        /****************************************************************************/
        val allNodes = getAllNodesBelow(a.ownerDocument)
        val ixA = IntStream.range(0, allNodes.size - 1).filter { ix: Int -> allNodes[ix] === a }.findFirst()
        if (!ixA.isPresent) throw StepExceptionWithStackTraceAbandonRun("getNodesBetween couldn't find node " + toString(a)) // For some reason, isEmpty gives a syntax error.



        /****************************************************************************/
        val res: MutableList<Node> = ArrayList()
        if (includeStart && !exclude.apply(a)) res.add(a)



        /****************************************************************************/
        val tryNode = IntConsumer { ix: Int ->
            val node = allNodes[ix]
            val isEndingNode = test.apply(node)
            var addToList = !myExclude.apply(node)
            if (addToList) addToList = !isEndingNode || includeEnd
            if (addToList) res.add(node)
            if (isEndingNode) throw StepExceptionWithStackTraceAbandonRun("")
        }

        try
        {
            IntStream.range(ixA.asInt + 1, allNodes.size - 1).forEach(tryNode)
        }
        catch (_: Exception)
        {
        }



        /****************************************************************************/
        return pruneToTopLevelOnly(res)
    }

    /****************************************************************************/
    @Throws(IOException::class)
    private fun output (s: String, fOut: BufferedWriter?)
    {
        if (null == fOut)
            Dbg.d(s)
        else
        {
            fOut.write(s)
            fOut.write("")
        }
    }

    /****************************************************************************/
    @Throws(IOException::class)
    private fun printParseTreeInternalWrapper (node: Node, attributeName: String?, attributeValue: String?, fOut: BufferedWriter?)
    {
        try
        {
          printParseTreeInternal("", 0, node, attributeName, attributeValue, fOut)
        }
        catch(_: Finished)
        {
        }
    }


    /****************************************************************************/
    private fun printParseTreeInternal (prefix: String, ordinalNumber: Int, node: Node, attributeName: String?, attributeValue: String?, fOut: BufferedWriter?)
    {
        var s = prefix
        s = if (0 == ordinalNumber /* Root */) "*" else s.substring(0, prefix.length - 1) + "*"

        if (0 != ordinalNumber) s += String.format("%4d. ", ordinalNumber)
        s += toString(node)
        output(s, fOut)
        val myPrefix = "$prefix    "
        val nl = node.childNodes
        for (i in 0..< nl.length) printParseTreeInternal(myPrefix, i + 1, nl.item(i), attributeName, attributeValue, fOut)
        if (null != attributeName)
        {
            val attrValue = getAttribute(node, attributeName)
            if (null != attrValue && attrValue.equals(attributeValue, ignoreCase = true)) throw Finished()
        }
    }


    /****************************************************************************/
    private class Finished : Exception()

    //private var m_Transformer: Transformer
    private fun makeTransformer (): Transformer
    {
       val res = TransformerFactory.newInstance().newTransformer()!!
       res.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
       res.setOutputProperty(OutputKeys.INDENT, "no")
       return res
    }

    //private var m_xPathFactory: XPathFactory
    private fun makeXPathFactory (): XPathFactory
    {
       //m_xPathFactory = new net.sf.saxon.xpath.XPathFactoryImpl()
       //m_xPathFactory = XPathFactory.newInstance()
       //m_xPathFactory = XPathFactory.newInstance("http://java.sun.com/jaxp/xpath/dom")
       //m_xPathFactory = net.sf.saxon.xpath.XPathFactoryImpl.newInstance()
       return XPathFactory.newInstance(NamespaceConstant.OBJECT_MODEL_SAXON)
    }


    init
    {
       System.setProperty("javax.xml.xpath.XPathFactory:" + NamespaceConstant.OBJECT_MODEL_SAXON, "net.sf.saxon.xpath.XPathFactoryImpl")
    }
}



/******************************************************************************/
/**
* A few useful extensions, which let you get or set attribute values using
* [...] syntax -- etc.  You'll need to import org.w3c.dom.Node to use them.
*/

operator fun Node.get (attributeName: String): String? { return Dom.getAttribute(this, attributeName) }           // val x = node[attrName"]
operator fun Node.set (attributeName: String, value: String) { Dom.setAttribute(this, attributeName, value) }     // node["attrName"] = value
operator fun Node.minus (attributeName: String): Node { Dom.deleteAttribute(this, attributeName); return this }   // node = node - "attrName" (delete attribute)
operator fun Node.minusAssign (attributeName: String) { Dom.deleteAttribute(this, attributeName) }                // node -= "attrName" (delete attribute)
operator fun Node.contains (attributeName: String): Boolean { return Dom.hasAttribute(this, attributeName) }      // "attrName" in node (or !in)

fun Node.getAllNodesBelow (): List<Node> = Dom.getAllNodesBelow(this)
fun Node.findNodeByAttributeName (nodeName: String, attributeName: String) = Dom.findNodeByAttributeName(this, nodeName, attributeName)
fun Node.findNodeByAttributeValue (nodeName: String, attributeName: String, attributeValue: String) = Dom.findNodeByAttributeValue(this, nodeName, attributeName, attributeValue)
fun Node.findNodeByName (nodeName: String, includeThisNode: Boolean) = Dom.findNodeByName(this, nodeName, includeThisNode)
fun Node.findNodesByAttributeName (nodeName: String, attributeName: String) = Dom.findNodesByAttributeName(this, nodeName, attributeName)
fun Node.findNodesByAttributeValue (nodeName: String, attributeName: String, attributeValue: String) = Dom.findNodesByAttributeValue(this, nodeName, attributeName, attributeValue)
fun Node.hasAncestorNamed (name: String) = Dom.hasAncestorNamed(this, name)
fun Node.isCommentNode () = Dom.isCommentNode(this)
fun Node.isWhitespace () = Dom.isWhitespace(this)
fun Node.isSiblingOf (node: Node) = Dom.isSiblingOf(this, node)
fun Node.stringify (node: Node) = Dom.toString(node)

fun Document.createNode (textForTag: String): Node = Dom.createNode(this, textForTag)
fun Document.getAllNodesBelow (): List<Node> = Dom.getAllNodesBelow(this)
fun Document.findNodeByName (nodeName: String) = Dom.findNodeByName(this, nodeName)
fun Document.findNodesByName (nodeName: String) = Dom.findNodesByName(this, nodeName)
fun Document.findNodesByAttributeName (nodeName: String, attributeName: String) = Dom.findNodesByAttributeName(this, nodeName, attributeName)
fun Document.findNodesByAttributeValue (nodeName: String, attributeName: String, attributeValue: String) = Dom.findNodesByAttributeValue(this, nodeName, attributeName, attributeValue)
