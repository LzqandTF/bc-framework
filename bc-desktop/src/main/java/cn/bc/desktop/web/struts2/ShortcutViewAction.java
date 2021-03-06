/**
 *
 */
package cn.bc.desktop.web.struts2;

import cn.bc.core.query.condition.Condition;
import cn.bc.core.query.condition.Direction;
import cn.bc.core.query.condition.impl.EqualsCondition;
import cn.bc.core.query.condition.impl.OrderCondition;
import cn.bc.db.jdbc.RowMapper;
import cn.bc.db.jdbc.SqlObject;
import cn.bc.identity.web.SystemContext;
import cn.bc.web.formater.BooleanFormater;
import cn.bc.web.struts2.ViewAction;
import cn.bc.web.ui.html.grid.Column;
import cn.bc.web.ui.html.grid.IdColumn4MapKey;
import cn.bc.web.ui.html.grid.TextColumn4MapKey;
import cn.bc.web.ui.html.page.PageOption;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 桌面管理视图
 *
 * @author dragon
 */
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Controller
public class ShortcutViewAction extends ViewAction<Map<String, Object>> {
	private static final long serialVersionUID = 1L;

	@Override
	public boolean isReadonly() {
		return false;// 所有人都可以管理自己的桌面
	}

	@Override
	protected String getHtmlPageTitle() {
		return this.getText("shortcut.title");
	}

	@Override
	protected String getHtmlPageNamespace() {
		return this.getContextPath() + "/bc/shortcut";
	}

	@Override
	protected PageOption getHtmlPageOption() {
		return super.getHtmlPageOption().setWidth(600).setMinWidth(300).setHeight(400).setMinHeight(200);
	}

	@Override
	protected OrderCondition getGridDefaultOrderCondition() {
		return new OrderCondition("order_", Direction.Asc);
	}

	@Override
	protected String[] getGridSearchFields() {
		return new String[]{"name", "url", "iconclass"};
	}

	@Override
	protected String getGridRowLabelExpression() {
		return "['name']";
	}

	@Override
	protected SqlObject<Map<String, Object>> getSqlObject() {
		SqlObject<Map<String, Object>> sqlObject = new SqlObject<>();

		// 构建查询语句,where和order by不要包含在sql中(要统一放到condition中)
		sqlObject.setSql("SELECT id, order_, standalone, name, url, iconclass FROM bc_desktop_shortcut");

		// 注入参数
		sqlObject.setArgs(null);

		// 数据映射器
		sqlObject.setRowMapper(new RowMapper<Map<String, Object>>() {
			public Map<String, Object> mapRow(Object[] rs, int rowNum) {
				Map<String, Object> map = new HashMap<>();
				int i = 0;
				map.put("id", rs[i++]);
				map.put("order_", rs[i++]);
				map.put("standalone", rs[i++]);
				map.put("name", rs[i++]);
				map.put("url", rs[i++]);
				map.put("iconclass", rs[i]);
				return map;
			}
		});
		return sqlObject;
	}

	@Override
	protected List<Column> getGridColumns() {
		List<Column> columns = new ArrayList<>();
		columns.add(new IdColumn4MapKey("id", "id"));
		columns.add(new TextColumn4MapKey("order_", "order_", getText("shortcut.order"), 80).setDir(Direction.Asc).setSortable(true));
		columns.add(new TextColumn4MapKey("standalone", "standalone", getText("shortcut.standalone"), 80).setSortable(true)
				.setValueFormater(new BooleanFormater(getText("shortcut.standalone.yes"), getText("shortcut.standalone.no"))));
		columns.add(new TextColumn4MapKey("name", "name", getText("shortcut.name"), 100).setSortable(true));
		columns.add(new TextColumn4MapKey("url", "url", getText("shortcut.url")).setSortable(true).setUseTitleFromLabel(true));
		columns.add(new TextColumn4MapKey("iconclass", "iconclass", getText("shortcut.iconClass"), 90).setSortable(true));
		return columns;
	}

	@Override
	protected Condition getGridSpecalCondition() {
		// 当前用户的桌面快捷方式
		return new EqualsCondition("aid", ((SystemContext) this.getContext()).getUser().getId());
	}
}