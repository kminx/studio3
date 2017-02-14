<!-- ...................................................................... -->
<!-- XHTML BDO Element Module ............................................. -->
<!-- file: xhtml-bdo-1.mod

     This is XHTML, a reformulation of HTML as a modular XML application.
     Copyright 1998-2001 W3C (MIT, INRIA, Keio), All Rights Reserved.
     Revision: $Id: xhtml-bdo-1.mod,v 1.1 2004/02/23 16:11:21 ylafon Exp $ SMI

     This DTD module is identified by the PUBLIC and SYSTEM identifiers:

       PUBLIC "-//W3C//ELEMENTS XHTML BDO Element 1.0//EN"
       SYSTEM "http://www.w3.org/TR/xhtml-modularization/DTD/xhtml-bdo-1.mod"

     Revisions:
     (none)
     ....................................................................... -->

<!-- Bidirectional Override (bdo) Element

     This modules declares the element 'bdo', used to override the
     Unicode bidirectional algorithm for selected fragments of text.

     DEPENDENCIES:
     Relies on the conditional section keyword %XHTML.bidi; declared
     as "INCLUDE". Bidirectional text support includes both the bdo
     element and the 'dir' attribute.
-->

<!ENTITY % bdo.element  "INCLUDE" >
<![%bdo.element;[
<!ENTITY % bdo.content
     "( #PCDATA | %Inline.mix; )*"
>
<!ENTITY % bdo.qname  "bdo" >
<!ELEMENT %bdo.qname;  %bdo.content; >
<!-- end of bdo.element -->]]>

<!ENTITY % bdo.attlist  "INCLUDE" >
<![%bdo.attlist;[
<!ATTLIST %bdo.qname;
      %Core.attrib;
      xml:lang     %LanguageCode.datatype;  #IMPLIED
      dir          ( ltr | rtl )            #REQUIRED
>
]]>

<!-- end of xhtml-bdo-1.mod -->
