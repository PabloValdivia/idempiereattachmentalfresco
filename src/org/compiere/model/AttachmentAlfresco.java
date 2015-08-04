package org.compiere.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import javax.mail.FolderNotFoundException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.ecosoft.cmis.CmisUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * @author a42niem
 * 
 * based on work by kittiu
 *
 */
public class AttachmentAlfresco implements IAttachmentStore {

	// CMIS by KTU

	private final CLogger log = CLogger.getCLogger(getClass());

	/**
	 * 	Load Data from CMIS
	 *	@return true if success
	 */
	@Override
	public boolean loadLOBData(MAttachment attach, MStorageProvider prov) {
			// Reset
			attach.m_items = new ArrayList<MAttachmentEntry>();
			//
			byte[] data = attach.getBinaryData();
			if (data == null)
				return true;
			log.fine("TextFileSize=" + data.length);
			if (data.length == 0)
				return true;
			
			Session session = CmisUtil.createCmisSession(prov.getUserName(), prov.getPassword(), prov.getURL());
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

			try 
			{
				final DocumentBuilder builder = factory.newDocumentBuilder();
				final Document document = builder.parse(new ByteArrayInputStream(data));
				final NodeList entries = document.getElementsByTagName("entry");
				for (int i = 0; i < entries.getLength(); i++) {
					final Node entryNode = entries.item(i);
					final NamedNodeMap attributes = entryNode.getAttributes();
					final Node docidNode = attributes.getNamedItem("docid");
					final Node fileNode = attributes.getNamedItem("file");
					final Node nameNode = attributes.getNamedItem("name");
					if(docidNode == null || fileNode==null || nameNode==null){
						log.severe("no filename for entry " + i);
						attach.m_items = null;
						return false;
					}
					log.fine("name: " + nameNode.getNodeValue());
					String docId = docidNode.getNodeValue();
					//docID = m_attachmentViewerUrlCMIS + docID;
					log.fine("docId: " + docId);
					
					org.apache.chemistry.opencmis.client.api.Document file;
					try
					{
						// Check document on CMIS
						List<org.apache.chemistry.opencmis.client.api.Document> versions;
						org.apache.chemistry.opencmis.client.api.Document version = 
								(org.apache.chemistry.opencmis.client.api.Document) session.getObject(session.createObjectId(docId));
						versions = version.getAllVersions();
						// Get only latest version
						file = versions.get(0);
					}
					catch (CmisObjectNotFoundException e)
					{
						log.severe(e.getMessage());
						file = null;
					}
					
					if (file != null) {
						
						try {
							InputStream fileInputStream = file.getContentStream().getStream();
							final byte[] dataEntry = CmisUtil.toByteArray(fileInputStream);
							fileInputStream.close();
							String name = file.getName();
							final MAttachmentEntry entry = new MAttachmentEntry(setDocId(name, docId), dataEntry, attach.m_items.size() + 1);
							attach.m_items.add(entry);
						} catch (FileNotFoundException e) {
							log.severe("File Not Found.");
							e.printStackTrace();
						} catch (IOException e1) {
							log.severe("Error Reading The File.");
							e1.printStackTrace();
						}
					} else {
						log.severe("Document ID not found: " + docId);
					}
					
				}

			} catch (SAXException sxe) {
				// Error generated during parsing)
				Exception x = sxe;
				if (sxe.getException() != null)
					x = sxe.getException();
				x.printStackTrace();
				log.severe(x.getMessage());

			} catch (ParserConfigurationException pce) {
				// Parser with specified options can't be built
				pce.printStackTrace();
				log.severe(pce.getMessage());

			} catch (IOException ioe) {
				// I/O error
				ioe.printStackTrace();
				log.severe(ioe.getMessage());
			}

			return true;
	}

	// CMIS by KTU
	/**
	 * 	Save Entry Data to the CMIS
	 *	@return true if saved
	 */
	@Override
	public boolean save(MAttachment attach, MStorageProvider prov) {

		if (attach.m_items == null || attach.m_items.size() == 0) {
			attach.setBinaryData(null);
			return true;
		}

		String cmisUser = prov.getUserName();
		String cmisPassword = prov.getPassword();
		String m_attachmentCMISUrl = prov.getURL();
		String cmisRootFolder = prov.getFolder();

		final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			Session session = CmisUtil.createCmisSession(cmisUser, cmisPassword, m_attachmentCMISUrl);
			try {
				final DocumentBuilder builder = factory.newDocumentBuilder();
				final Document document = builder.newDocument();
				final Element root = document.createElement("attachments");
				document.appendChild(root);
				document.setXmlStandalone(true);
				
				// Get the source window information, to be saved as alfresco's document properties.
				String tableName = MTable.getTableName(Env.getCtx(), attach.getAD_Table_ID());
				String recordId = ((Integer)attach.getRecord_ID()).toString();
				
				// create xml entries
				for (int i = 0; i < attach.m_items.size(); i++) {
					log.fine(attach.m_items.get(i).toString());
					
					MAttachmentEntry item = attach.m_items.get(i);
					String docId = getDocId(item);
					
					if (docId.equals("")) // Not exists, create new document.
					{
						File entryFile = attach.m_items.get(i).getFile();
						final String path = entryFile.getAbsolutePath();
						org.apache.chemistry.opencmis.client.api.Document cmisDoc = null;
						// if local file - copy to central attachment folder
						log.fine(path + " - " + m_attachmentCMISUrl);

						log.fine("move file: " + path);
						String mimeType = null;
						byte[] content = null;
						try {
							// Prepare file / folder
							content = item.getData();
							mimeType = item.getContentType();
							Folder folder = (Folder) CmisUtil.getFolder(session, cmisRootFolder);
							if (folder == null)
								throw new FolderNotFoundException();
							
							// Upload to CMIS
							cmisDoc = CmisUtil.createiDempiereAttachment(session, folder, attach.getEntryName(i), mimeType, content, tableName, recordId);
							docId = cmisDoc.getId().substring(0, cmisDoc.getId().lastIndexOf(";")); // Remove ";<version>"
							
						} catch (IOException e) {
							e.printStackTrace();
							log.severe("unable to copy file " + entryFile.getAbsolutePath() + " to "
									+ m_attachmentCMISUrl + File.separator + 
									getAttachmentPathSnippet(attach) + File.separator + entryFile.getName());
						} 
					}
					final Element entry = document.createElement("entry");
					//entry.setAttribute("name", m_items.get(i).getName());
					entry.setAttribute("name", attach.getEntryName(i));
					entry.setAttribute("file", attach.getEntryName(i)); // File
					entry.setAttribute("docid", docId);
					root.appendChild(entry);
				}

				final Source source = new DOMSource(document);
				final ByteArrayOutputStream bos = new ByteArrayOutputStream();
				final Result result = new StreamResult(bos);
				final Transformer xformer = TransformerFactory.newInstance().newTransformer();
				xformer.transform(source, result);
				final byte[] xmlData = bos.toByteArray();
				log.fine(bos.toString());
				attach.setBinaryData(xmlData);
				return true;
			} catch (FolderNotFoundException e) {
				log.log(Level.SEVERE, "CMIS Root Folder Not Found!");
			} catch (Exception e) {
				log.log(Level.SEVERE, "saveLOBData", e);
			}
			session = null;
			attach.setBinaryData(null);
			return false;
	}

	/**
	 * Returns a path snippet, containing client, org, table and record id.
	 * @param attach 
	 * @return String
	 */
	private String getAttachmentPathSnippet(MAttachment attach){
		return attach.getAD_Client_ID() + File.separator + 
		attach.getAD_Org_ID() + File.separator + 
		attach.getAD_Table_ID() + File.separator + attach.getRecord_ID();
	}

	@Override
	public boolean delete(MAttachment attach, MStorageProvider prov) {
		//delete all attachment files
		for (int i=0; i<attach.m_items.size(); i++) {
			final MAttachmentEntry entry = attach.m_items.get(i);
			
			Session session = CmisUtil.createCmisSession(prov.getUserName(), prov.getPassword(), prov.getURL());
			org.apache.chemistry.opencmis.client.api.Document file = 
					(org.apache.chemistry.opencmis.client.api.Document) session.getObject(session.createObjectId(getDocId(entry)));
			
			log.fine("delete: " + file.getName());
			if(file !=null){
				try {
					file.deleteAllVersions();
				} catch (Exception e) {
					log.warning("unable to delete " + file.getName());
				}
			}
		}
		return true;
	}

	@Override
	public boolean deleteEntry(MAttachment attach, MStorageProvider prov, int index) {
		if (index >= 0 && index < attach.m_items.size()) {
			// CMIS by KTU
			//remove files
			final MAttachmentEntry entry = attach.m_items.get(index);

			// Connect
			Session session = CmisUtil.createCmisSession(prov.getUserName(), prov.getPassword(), prov.getURL());
			org.apache.chemistry.opencmis.client.api.Document file = 
					(org.apache.chemistry.opencmis.client.api.Document) session.getObject(session.createObjectId(getDocId(entry)));

			log.fine("delete: " + file.getName());
			if(file !=null){
				try {
					file.deleteAllVersions();
				} catch (Exception e) {
					log.warning("unable to delete " + file.getName());
				}
			}

			// --
			attach.m_items.remove(index);
			log.config("Index=" + index + " - NewSize=" + attach.m_items.size());
			return true;
		}
		log.warning( "Not deleted Index=" + index + " - Size=" + attach.m_items.size());
		return false;
	}
	
	/**
	 * @return Returns the docid.
	 */
	public static String getDocId (MAttachmentEntry entry)
	{
		String name = entry.getName();
		String docid = "";
		if (name.indexOf("!") > -1)
			docid = name.split("!")[0];
		return docid;
	}
	
	/**
	 * @param name The docid to set.
	 */
	private String setDocId (String name, String docid)
	{
		if (docid == null)
			docid = "";
        name = docid.concat("!").concat(name);
        return name;
	}	//	setDocId

}
