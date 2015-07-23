/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package org.compiere.model;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.logging.Level;

import org.compiere.util.CLogger;
import org.compiere.util.MimeType;


/**
 *	Individual Attachment Entry of MAttachment
 *	
 *  @author Jorg Janke
 *  @version $Id: MAttachmentEntry.java,v 1.2 2006/07/30 00:58:18 jjanke Exp $
 */
public class MAttachmentEntryEcosoft extends MAttachmentEntry
{
	/**
	 * 	Attachment Entry
	 * 	@param name name
	 * 	@param data binary data
	 * 	@param index optional index
	 */
	public MAttachmentEntryEcosoft (String name, byte[] data, int index)
	{
		super (name, data, index);
	}	//	MAttachmentItem
	
	/**
	 * 	Attachment Entry
	 * 	@param name name
	 * 	@param data binary data
	 */
	public MAttachmentEntryEcosoft (String name, byte[] data)
	{
		this (name, data, 0);
	}	//	MAttachmentItem
		
	// CMIS by KTU
	public MAttachmentEntryEcosoft (String name, byte[] data, String docid, int index)
	{
		this (name, data, index);
		setDocId (docid);
	}	//	MAttachmentItem
	
	/**	The docid				*/
	private String 	m_docid = "";
	
	/**
	 * @return Returns the docid.
	 */
	public String getDocId ()
	{
		return m_docid;
	}
	
	/**
	 * @param name The docid to set.
	 */
	public void setDocId (String docid)
	{
		if (docid != null)
			m_docid = docid;
		if (docid == null)
			m_docid = "";
	}	//	setDocId
	// --
	
}	//	MAttachmentItem
