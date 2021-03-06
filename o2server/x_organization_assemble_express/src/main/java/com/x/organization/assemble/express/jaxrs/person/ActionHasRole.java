package com.x.organization.assemble.express.jaxrs.person;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.commons.lang3.StringUtils;

import com.google.gson.JsonElement;
import com.x.base.core.container.EntityManagerContainer;
import com.x.base.core.container.factory.EntityManagerContainerFactory;
import com.x.base.core.entity.JpaObject;
import com.x.base.core.project.annotation.FieldDescribe;
import com.x.base.core.project.cache.ApplicationCache;
import com.x.base.core.project.gson.GsonPropertyObject;
import com.x.base.core.project.http.ActionResult;
import com.x.base.core.project.http.EffectivePerson;
import com.x.base.core.project.jaxrs.WrapBoolean;
import com.x.base.core.project.tools.ListTools;
import com.x.organization.assemble.express.Business;
import com.x.organization.core.entity.Person;
import com.x.organization.core.entity.Role;
import com.x.organization.core.entity.Role_;

import net.sf.ehcache.Element;

class ActionHasRole extends BaseAction {

	ActionResult<Wo> execute(EffectivePerson effectivePerson, JsonElement jsonElement) throws Exception {
		try (EntityManagerContainer emc = EntityManagerContainerFactory.instance().create()) {
			Wi wi = this.convertToWrapIn(jsonElement, Wi.class);
			ActionResult<Wo> result = new ActionResult<>();
			Business business = new Business(emc);
			String cacheKey = ApplicationCache.concreteCacheKey(this.getClass(), wi.getPerson(),
					StringUtils.join(wi.getRoleList(), ","));
			Element element = cache.get(cacheKey);
			if (null != element && (null != element.getObjectValue())) {
				result.setData((Wo) element.getObjectValue());
			} else {
				Wo wo = this.get(business, wi);
				cache.put(new Element(cacheKey, wo));
				result.setData(wo);
			}
			return result;
		}
	}

	public static class Wi extends GsonPropertyObject {

		@FieldDescribe("个人")
		private String person;

		@FieldDescribe("角色")
		private List<String> roleList = new ArrayList<>();

		public String getPerson() {
			return person;
		}

		public void setPerson(String person) {
			this.person = person;
		}

		public List<String> getRoleList() {
			return roleList;
		}

		public void setRoleList(List<String> roleList) {
			this.roleList = roleList;
		}

	}

	public static class Wo extends WrapBoolean {

	}

	private Wo get(Business business, Wi wi) throws Exception {
		Wo wo = new Wo();
		wo.setValue(false);
		if (StringUtils.isEmpty(wi.getPerson()) || ListTools.isEmpty(wi.getRoleList())) {
			return wo;
		}
		Person person = business.person().pick(wi.getPerson());
		if (null == person) {
			return wo;
		}
		List<Role> roles = business.role().pick(wi.getRoleList());
		if (ListTools.isEmpty(roles)) {
			return wo;
		}
		List<String> groupIds = new ArrayList<>();
		groupIds.addAll(business.group().listSupNestedWithPerson(person.getId()));
		groupIds = ListTools.trim(groupIds, true, true);
		EntityManager em = business.entityManagerContainer().get(Role.class);
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<String> cq = cb.createQuery(String.class);
		Root<Role> root = cq.from(Role.class);
		Predicate p = cb.isMember(person.getId(), root.get(Role_.personList));
		p = cb.or(p, root.get(Role_.groupList).in(groupIds));
		List<String> os = em.createQuery(cq.select(root.get(Role_.id)).where(p).distinct(true)).getResultList();
		boolean value = ListTools.containsAny(os,
				ListTools.extractProperty(roles, JpaObject.id_FIELDNAME, String.class, true, true));
		wo.setValue(value);
		return wo;
	}

}