package de.action42.idempiere.cmis.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
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
import org.compiere.model.IAttachmentStore;
import org.compiere.model.I_AD_Attachment;
import org.compiere.model.MAttachment;
import org.compiere.model.MAttachmentEntry;
import org.compiere.model.MStorageProvider;
import org.compiere.model.MTable;
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
 * @author a42niem - Dirk Niemeyer - action42 GmbH
 * 
 * based on work done by kittiu for Alfresco 4 (see http://www.adempiere.com/index.php?title=ADempiere_Integration_With_Alfresco) 
 * adapted to Alfresco 5 and extended
 * 
 */

public class AttachmentAlfresco implements IAttachmentStore {

	// CMIS by KTU
	// Adapted to Alfresco 5 and enhanced by Dirk Niemeyer / a42niem

	private final CLogger log = CLogger.getCLogger(getClass());

	Node m_docidNode = null;
	Node m_fileNode = null;
	Node m_nameNode = null;
	Node m_versionNode = null;
	Node m_md5Node = null;
	
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
					getEntryData(entries.item(i));
					if(m_docidNode == null || m_fileNode==null || m_nameNode==null){
						log.severe("no filename for entry " + i);
						attach.m_items = null;
						return false;
					}
					log.fine("name: " + m_nameNode.getNodeValue());
					String docId = m_docidNode.getNodeValue();
					if (m_versionNode != null) {
						docId = docId + ";" + m_versionNode.getNodeValue();
					}
					log.fine("docId: " + docId);


					org.apache.chemistry.opencmis.client.api.Document file;
					try
					{	
						// Check document on CMIS
						org.apache.chemistry.opencmis.client.api.Document version = 
								(org.apache.chemistry.opencmis.client.api.Document) session.getObject(session.createObjectId(docId));
						if (m_versionNode==null) {
							List<org.apache.chemistry.opencmis.client.api.Document> versions;
							versions = version.getAllVersions();
							// Get only latest version
							file = versions.get(0);
						}
						else {
							file = version;
						}
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
							final MAttachmentEntry entry = new MAttachmentEntry(name, dataEntry, attach.m_items.size() + 1);
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
				getEntryData((byte[])attach.get_ValueOld(I_AD_Attachment.COLUMNNAME_BinaryData), i);
				String docId =  (m_docidNode!=null) ? m_docidNode.getNodeValue() : "";
				String docVersion = (m_versionNode!=null) ? m_versionNode.getNodeValue() : "";
				String docMd5 = (m_md5Node!=null) ? m_md5Node.getNodeValue() : "";
				byte[] content = item.getData();
				MessageDigest md5Digest = MessageDigest.getInstance("MD5");
				String checksum = getFileChecksum(md5Digest, content);
				
				// If not exists or is different, create or update document.
				if (docId.equals("") || !docMd5.equals(checksum)) 
				{
					File entryFile = attach.m_items.get(i).getFile();
					final String path = entryFile.getAbsolutePath();
					org.apache.chemistry.opencmis.client.api.Document cmisDoc = null;
					// if local file - copy to central attachment folder
					log.fine(path + " - " + m_attachmentCMISUrl);

					log.fine("move file: " + path);
					String mimeType = null;
					try {
						// Prepare file / folder
						mimeType = item.getContentType();
						Folder folder = (Folder) CmisUtil.getFolder(session, cmisRootFolder);
						if (folder == null)
							throw new FolderNotFoundException();

						// Upload to CMIS
						cmisDoc = CmisUtil.createiDempiereAttachment(session, folder, attach.getEntryName(i), mimeType, content, tableName, recordId, checksum);
						docId = cmisDoc.getId().substring(0, cmisDoc.getId().lastIndexOf(";")); // Remove ";<version>"
						docVersion = cmisDoc.getId().substring(cmisDoc.getId().lastIndexOf(";")+1); // Get "<version>"
						docMd5 = checksum;
					} catch (IOException e) {
						e.printStackTrace();
						log.severe("unable to copy file " + entryFile.getAbsolutePath() + " to "
								+ m_attachmentCMISUrl + File.separator + 
								getAttachmentPathSnippet(attach) + File.separator + entryFile.getName());
					} 
				}
				final Element entry = document.createElement("entry");
				entry.setAttribute("name", attach.getEntryName(i));
				entry.setAttribute("file", attach.getEntryName(i)); // File
				entry.setAttribute("docid", docId);
				entry.setAttribute("version", docVersion);
				entry.setAttribute("md5", docMd5);
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
	 * Get Entry Data from one Node
	 * 
	 * @param item
	 */
	private void getEntryData(Node item) {
		final Node entryNode = item;
		if (entryNode == null)
			return;
		final NamedNodeMap attributes = entryNode.getAttributes();
		m_docidNode = attributes.getNamedItem("docid");
		m_fileNode = attributes.getNamedItem("file");
		m_nameNode = attributes.getNamedItem("name");
		m_versionNode = attributes.getNamedItem("version");
		m_md5Node = attributes.getNamedItem("md5");
	}

	/**
	 * Get Entry Data from BinaryData
	 * @param data
	 * @param index
	 */
	private void getEntryData(byte[] data, int index) {

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		try 
		{
			// reset all
			m_docidNode = null;
			m_fileNode = null;
			m_nameNode = null;
			m_versionNode = null;
			m_md5Node = null;
			final DocumentBuilder builder = factory.newDocumentBuilder();
			final Document document = builder.parse(new ByteArrayInputStream(data));
			final NodeList entries = document.getElementsByTagName("entry");
			getEntryData(entries.item(index));

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

	}

	/**
	 * Prepare checksum
	 * 
	 * @param digest
	 * @param content
	 * @return checksum
	 * @throws IOException
	 * 
	 */
	private String getFileChecksum(MessageDigest digest, byte[] content) throws IOException
	{
		// update the digest
	    digest.update(content); 
	     
	    //Get the hash's bytes
	    byte[] bytes = digest.digest();
	    
	    //This bytes[] has bytes in decimal format;
	    //Convert it to hexadecimal format
	    StringBuilder sb = new StringBuilder();
	    for(int i=0; i< bytes.length ;i++)
	    {
	        sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
	    }	
	    
	    //return complete hash
	   return sb.toString();
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
					(org.apache.chemistry.opencmis.client.api.Document) session.getObject(session.createObjectId(getDocId(attach, i)));
			
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
			//remove files
			final MAttachmentEntry entry = attach.m_items.get(index);

			// Connect
			Session session = CmisUtil.createCmisSession(prov.getUserName(), prov.getPassword(), prov.getURL());
			org.apache.chemistry.opencmis.client.api.Document file = 
					(org.apache.chemistry.opencmis.client.api.Document) session.getObject(session.createObjectId(getDocId(attach, index)));

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
	 * 
	 * @param index 
	 * @return Returns the docId.
	 */
	private String getDocId(MAttachment att, int index) {

		String docId = "";
		byte[] data = att.getBinaryData();
		if (data == null)
			return "";
		log.fine("TextFileSize=" + data.length);
		if (data.length == 0)
			return "";
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		try 
		{
			final DocumentBuilder builder = factory.newDocumentBuilder();
			final Document document = builder.parse(new ByteArrayInputStream(data));
			final NodeList entries = document.getElementsByTagName("entry");
			final Node entryNode = entries.item(index);
			final NamedNodeMap attributes = entryNode.getAttributes();
			final Node docidNode = attributes.getNamedItem("docid");
			final Node fileNode = attributes.getNamedItem("file");
			final Node nameNode = attributes.getNamedItem("name");
			if(docidNode == null || fileNode==null || nameNode==null){
				log.severe("no date for entry " + index);
				att.m_items = null;
				return "";
			}
			log.fine("name: " + nameNode.getNodeValue());
			docId = docidNode.getNodeValue();
			log.fine("docId: " + docId);
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
		return docId;
	}

	
}
