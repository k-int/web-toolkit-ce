package com.k_int.web.toolkit.rest

import grails.artefact.Artefact
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.transactions.Transactional


@CurrentTenant
@Artefact('Controller')
class TenantAwareRestfulController<T> extends RestfulController<T> {

  TenantAwareRestfulController(Class<T> resource) {
    this(resource, false)
  }

  TenantAwareRestfulController(Class<T> resource, boolean readOnly) {
    super(resource, readOnly)
  }

  def index(Integer max) {
    super.index(max)
  }
  
  protected getObjectToBind() {
    super.getObjectToBind()
  }

  def show() {
    super.show()
  }

  def create() {
    super.create()
  }

  @Transactional
  def save() {
    super.save()
  }

  def edit() {
    super.edit()
  }

  @Transactional
  def patch() {
    super.patch()
  }

  @Transactional
  def update() {
    super.update()
  }

  @Transactional
  def delete() {
    super.delete()
  }

  protected T queryForResource(Serializable id) {
    super.queryForResource(id)
  }

  protected T createResource(Map params) {
    super.createResource(params)
  }
  protected T createResource() {
    super.createResource()
  }
  protected List<T> listAllResources(Map params) {
    super.listAllResources(params)
  }

  protected Integer countResources() {
    super.countResources()
  }

  protected void notFound() {
    super.notFound()
  }

  protected T saveResource(T resource) {
    super.saveResource(resource)
  }

  protected T updateResource(T resource) {
    super.updateResource(resource)
  }

  protected void deleteResource(T resource) {
    super.deleteResource(resource)
  }

  protected String getClassMessageArg() {
    super.getClassMessageArg()
  }
}
