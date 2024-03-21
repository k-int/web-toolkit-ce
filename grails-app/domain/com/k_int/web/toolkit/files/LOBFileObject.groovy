package com.k_int.web.toolkit.files
import java.sql.Blob

import javax.persistence.Lob

import org.grails.datastore.gorm.GormEntity
import org.hibernate.engine.jdbc.BlobProxy
import org.springframework.web.multipart.MultipartFile

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity


@GrailsCompileStatic
class LOBFileObject extends FileObject implements GormEntity<FileObject>, MultiTenant<FileObject>, Clonable<LOBFileObject> {

	static cloneStaticValues = [
		fileContents: {
			LOBFileObject theOwner = owner as LOBFileObject
			BlobProxy.generateProxy(theOwner.fileContents.getBinaryStream(), theOwner.fileContents.length())
		}
	]

	@Lob
	Blob fileContents

	void setFileContents ( Blob fileContents ) {
		this.fileContents = fileContents
	}

	void setFileContents( InputStream is, long length ) {
		setFileContents( BlobProxy.generateProxy(is, length) )
	}

	void setFileContents( MultipartFile file ) {
		setFileContents( file.inputStream, file.size )
	}

	static constraints = {
		fileContents nullable: false
	}

	static mapping = {
		discriminator "DB"
	}

	@Override
	public LOBFileObject clone () {
		Clonable.super.clone()
	}
}
