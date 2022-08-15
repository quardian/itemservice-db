package hello.itemservice.repository.jdbctemplate;

import hello.itemservice.domain.Item;
import hello.itemservice.repository.ItemRepository;
import hello.itemservice.repository.ItemSearchCond;
import hello.itemservice.repository.ItemUpdateDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SimpleJdbcInsert
 */
@Slf4j
public class JdbcTemplateItemRepositoryV3 implements ItemRepository {

    private final NamedParameterJdbcTemplate template;
    private final SimpleJdbcInsert jdbcInsert;
    
    public JdbcTemplateItemRepositoryV3(DataSource dataSource) {
        this.template = new NamedParameterJdbcTemplate(dataSource);
        this.jdbcInsert = new SimpleJdbcInsert(dataSource)
                .withTableName("item")
                .usingGeneratedKeyColumns("id")
                .usingColumns("item_name", "price", "quantity"); // <--생략 가능
    }

    @Override
    public void save(Item item) {
        SqlParameterSource param = new BeanPropertySqlParameterSource(item);
        Number key = jdbcInsert.executeAndReturnKey(param);

        item.setId(key.longValue());
    }

    @Override
    public void update(Long itemId, ItemUpdateDto updateParam) {
        String sql = "update item set " +
                "item_name=:itemName, " +
                "price=:price, " +
                "quantity=:quantity " +
                "where id=:id";

        SqlParameterSource param = new MapSqlParameterSource()
                .addValue("itemName", updateParam.getItemName())
                .addValue("price", updateParam.getPrice())
                .addValue("quantity", updateParam.getQuantity())
                .addValue("id", itemId);

        int affected = template.update(sql,param);
        log.info("ITEM updated affected = {}", affected);
    }

    @Override
    public Optional<Item> findById(Long id) {
        String sql = "select id, item_name, price, quantity from item where id = :id";
        try{
            Map<String, Long> param = Map.of("id", id);
            Item item = template.queryForObject(sql, param, itemRowMapper());
            return Optional.of(item);
        }
        catch (EmptyResultDataAccessException e){
            // 결과가 없는 경우 예외
            return Optional.empty();
        }
        catch (IncorrectResultSizeDataAccessException e){
            // 결과가 둘 이상인 경우 예외
            throw e;
        }

    }

    @Override
    public List<Item> findAll(ItemSearchCond cond) {
        String itemName  = cond.getItemName();
        Integer maxPrice = cond.getMaxPrice();

        StringBuilder sbSql = new StringBuilder();
        sbSql.append("select id, item_name, price, quantity from item");

        SqlParameterSource param = new BeanPropertySqlParameterSource(cond);
        // 동적쿼리 시작 ==============================================
        if (StringUtils.hasText(itemName) || maxPrice != null ){
            sbSql.append(" where ");
        }
        boolean andFalg = false;
        if ( StringUtils.hasText((itemName)) ){
            sbSql.append( " item_name like concat('%',:itemName,'%') ");
            andFalg = true;
        }
        if ( maxPrice != null ){
            if (andFalg){
                sbSql.append( " and ");
            }
            sbSql.append(" price <= :maxPrice ");
        }
        // 동적쿼리 종료 ==============================================
        log.info("findAll query = {}", sbSql.toString() );
        return template.query( sbSql.toString(), param, itemRowMapper() );
    }


    private RowMapper<Item> itemRowMapper() {
        return BeanPropertyRowMapper.newInstance(Item.class); //camel지원
    }
}
