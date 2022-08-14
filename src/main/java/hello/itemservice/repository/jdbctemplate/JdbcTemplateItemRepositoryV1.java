package hello.itemservice.repository.jdbctemplate;

import hello.itemservice.domain.Item;
import hello.itemservice.repository.ItemRepository;
import hello.itemservice.repository.ItemSearchCond;
import hello.itemservice.repository.ItemUpdateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JdbcTemplate
 */
@Slf4j
public class JdbcTemplateItemRepositoryV1 implements ItemRepository {

    private final JdbcTemplate template;

    public JdbcTemplateItemRepositoryV1(DataSource dataSource) {
        this.template = new JdbcTemplate(dataSource);
    }

    @Override
    public Item save(Item item) {
        String sql = "insert into item(item_name, price, quantity) values (?,?,?);";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        template.update( connection -> {
            // 자동 증가 키
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, item.getItemName());
            ps.setInt(2, item.getPrice());
            ps.setInt(3, item.getQuantity());
            return ps;
        }, keyHolder);

        long key = keyHolder.getKey().longValue();
        item.setId(key);
        log.info("ITEM inserted key = {}", key);
        return item;
    }

    @Override
    public void update(Long itemId, ItemUpdateDto updateParam) {
        String sql = "update item set item_name=?, price=?, quantity=? where id=?";
        int affected = template.update(sql,
                updateParam.getItemName(), updateParam.getPrice(), updateParam.getQuantity(),
                itemId);
        log.info("ITEM updated affected = {}", affected);
    }

    @Override
    public Optional<Item> findById(Long id) {
        String sql = "select id, item_name, price, quantity from item where id = ?";
        try{
            Item item = template.queryForObject(sql, itemRowMapper(), id);
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

        // 동적쿼리 시작 ==============================================
        if (StringUtils.hasText(itemName) || maxPrice != null ){
            sbSql.append(" where ");
        }
        boolean andFalg = false;
        List<Object> params = new ArrayList<>();

        if ( StringUtils.hasText((itemName)) ){
            sbSql.append( " item_name like concat('%',?,'%') ");
            params.add(itemName);
            andFalg = true;
        }
        if ( maxPrice != null ){
            if (andFalg){
                sbSql.append( " and ");
            }
            sbSql.append(" price <= ? ");
            params.add(maxPrice);
        }
        // 동적쿼리 종료 ==============================================
        log.info("findAll query = {}", sbSql.toString() );
        return template.query(sbSql.toString(), itemRowMapper(), params.toArray());
    }


    private RowMapper<Item> itemRowMapper() {
        return ((rs, rowNum) -> {
            Item item = new Item();
            item.setId( rs.getLong("id") );
            item.setItemName( rs.getString("item_name") );
            item.setPrice( rs.getInt("price") );
            item.setQuantity( rs.getInt("quantity") );
            return item;
        });
    }
}
