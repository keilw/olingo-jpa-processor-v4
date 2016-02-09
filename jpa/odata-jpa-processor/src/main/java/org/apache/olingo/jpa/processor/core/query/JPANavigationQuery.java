package org.apache.olingo.jpa.processor.core.query;

import java.util.List;
import java.util.Locale;

import javax.persistence.EntityManager;
import javax.persistence.criteria.AbstractQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPAElement;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPAOnConditionItem;
import org.apache.olingo.jpa.metadata.core.edm.mapper.exception.ODataJPAModelException;
import org.apache.olingo.jpa.metadata.core.edm.mapper.impl.JPAAssociationPath;
import org.apache.olingo.jpa.metadata.core.edm.mapper.impl.ServicDocument;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.UriParameter;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourcePartTyped;

/**
 * Creates a sub query for a navigation.
 * @author Oliver Grande
 *
 */
public class JPANavigationQuery extends JPAAbstractQuery {
  private final List<UriParameter> keyPredicates;
  private final JPAAssociationPath association;
  private Root<?> queryRoot;
  private Subquery<?> subQuery;
  private JPAAbstractQuery parentQuery;

  public JPANavigationQuery(final ServicDocument sd, final UriResource uriResourceItem, 
      final EntityManager em, JPAAssociationPath association) throws ODataApplicationException {
    super(sd, ((UriResourcePartTyped) uriResourceItem).getType(), em);
    this.keyPredicates = determineKeyPredicates(uriResourceItem);
    this.association = association;
  }

  public <T extends Object> JPANavigationQuery(final ServicDocument sd, final UriResource uriResourceItem, final JPAAbstractQuery parent,
     final EntityManager em, final JPAAssociationPath association) throws ODataApplicationException {
    
    super(sd, ((UriResourcePartTyped) uriResourceItem).getType(), em);
    this.keyPredicates = determineKeyPredicates(uriResourceItem);
    this.association = association;
    this.parentQuery = parent;
    this.subQuery = parent.getQuery().subquery(this.jpaEntity.getKeyType());
    this.queryRoot = subQuery.from(this.jpaEntity.getTypeClass());
  }

  /**
   * @return
   */
  @Override
  protected Root<?> getRoot() {
    assert queryRoot != null;
    return queryRoot;
  }

  @Override
  public AbstractQuery<?> getQuery() {
    return subQuery;
  }

  /**
   * Creates a sub query that can be used within an EXISTS condition.
   * @return
   * @throws ODataApplicationException
   */
  @SuppressWarnings("unchecked")
  public <T extends Object> Subquery<T> getSubQueryExists(Subquery<?> childQuery) throws ODataApplicationException {
    Subquery<T> subQuery = (Subquery<T>) this.subQuery; 

    List<JPAOnConditionItem> conditionItems;
    try {
      conditionItems = association.getJoinColumnsList();
      Path<?> p = queryRoot;
      for (JPAElement jpaPathElement : conditionItems.get(0).getLeftPath().getPath())
        p = p.get(jpaPathElement.getInternalName());
      subQuery.select((Expression<T>) p);
    } catch (ODataJPAModelException e) {
      // TODO Update error handling
      throw new ODataApplicationException("Unknown navigation property", HttpStatusCode.INTERNAL_SERVER_ERROR.ordinal(),
          Locale.ENGLISH, e);
    }

    Expression<Boolean> whereCondition = null;
    if (this.keyPredicates == null || this.keyPredicates.isEmpty())
      whereCondition = createWhereByAssociation(parentQuery.getRoot(), queryRoot, conditionItems);
    else
      whereCondition = cb.and(
          createWhereByKey(queryRoot, null, this.keyPredicates),
          createWhereByAssociation(parentQuery.getRoot(), queryRoot, conditionItems));
    if (childQuery != null)
      whereCondition = cb.and(whereCondition, cb.exists(childQuery));
    subQuery.where(whereCondition);
    return (Subquery<T>) subQuery;
  }

  private Expression<Boolean> createWhereByAssociation(From<?, ?> parentFrom, Root<?> subRoot,
      List<JPAOnConditionItem> conditionItems) {
    Expression<Boolean> whereCondition = null;
    for (JPAOnConditionItem onItem : conditionItems) {
      Path<?> paretPath = parentFrom;
      for (JPAElement jpaPathElement : onItem.getRightPath().getPath())
        paretPath = paretPath.get(jpaPathElement.getInternalName());
      Path<?> subPath = subRoot;
      for (JPAElement jpaPathElement : onItem.getLeftPath().getPath())
        subPath = subPath.get(jpaPathElement.getInternalName());

      Expression<Boolean> equalCondition = cb.equal(
          paretPath, subPath);
//          parentFrom.get(onItem.getRightPath().getInternalName()),
//          subRoot.get(onItem.getLeftPath().getInternalName()));
      if (whereCondition == null)
        whereCondition = equalCondition;
      else
        whereCondition = cb.and(whereCondition, equalCondition);
    }
    return whereCondition;
  }
}