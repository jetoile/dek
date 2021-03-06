package fr.layer4.dek.property;

/*-
 * #%L
 * DEK
 * %%
 * Copyright (C) 2018 Layer4
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import fr.layer4.dek.events.StoreReadyEvent;
import fr.layer4.dek.store.LocalSecuredStore;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Getter
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class LocalPropertyManager implements PropertyManager, ApplicationListener<StoreReadyEvent> {

    private final LocalSecuredStore localSecuredStore;

    public static void updateDdl(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.batchUpdate(
                "CREATE TABLE IF NOT EXISTS env(id INT AUTO_INCREMENT PRIMARY KEY, key VARCHAR(255), value VARCHAR(255))");
    }

    public static void updateData(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.batchUpdate(
                "MERGE INTO env KEY (`key`) VALUES "
                        + "(default, 'http.socket.timeout', '30000'),"
                        + "(default, 'http.connect.timeout', '30000'),"
                        + "(default, 'proxy.enabled', 'false')"
        );
    }

    @Override
    public void onApplicationEvent(StoreReadyEvent storeReadyEvent) {
        updateDdl(this.localSecuredStore.getJdbcTemplate());
        updateData(this.localSecuredStore.getJdbcTemplate());
    }

    @Override
    public Map<String, String> getProperty() {
        List<Map<String, Object>> maps = this.localSecuredStore.getJdbcTemplate().queryForList("SELECT `key`, `value` FROM env");
        return maps.stream().map(e -> Pair.of((String) e.get("KEY"), (String) e.get("VALUE"))).collect(Collectors.toMap(Pair::getKey, Pair::getRight));
    }

    @Override
    public Optional<String> getProperty(String key) {
        try {
            return Optional.of(this.localSecuredStore.getJdbcTemplate().queryForObject("SELECT `value` FROM env WHERE `key` = ?", String.class, key));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public void setProperty(String key, String value) {
        this.localSecuredStore.getJdbcTemplate().update("MERGE INTO env KEY (`key`) VALUES (default, ?, ?);", key, value);
    }

    @Override
    public void deleteProperty(String key) {
        this.localSecuredStore.getJdbcTemplate().update("DELETE env WHERE `key` = ?", key);
    }
}
