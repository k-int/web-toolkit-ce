package com.k_int.web.toolkit.files

import org.grails.datastore.gorm.GormEntity

import com.k_int.web.toolkit.domain.traits.Clonable

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant

@GrailsCompileStatic
class S3FileObject extends FileObject implements GormEntity<FileObject>, MultiTenant<FileObject>, Clonable<S3FileObject> {

	String s3ref

	static constraints = {
		s3ref nullable: false
	}

	static mapping = {
		discriminator "S3"
		s3ref column: 'fo_s3ref'
	}

	@Override
	public S3FileObject clone () {
		Clonable.super.clone()
	}
}
