package kr.sedanji.publicweb;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import static org.assertj.core.api.Assertions.*;

class PublicInputTest {
    LinkedMultiValueMap<String,String> bounds() {
        var p=new LinkedMultiValueMap<String,String>();
        p.add("west","126.9"); p.add("south","37.5"); p.add("east","127.15"); p.add("north","37.7"); return p;
    }
    @Test void exactLimitsAndDecimalPrecision() {
        assertThat(PublicInput.bounds(bounds()).east()).isEqualTo(127.15);
        var scientific=bounds(); scientific.set("west","1.269e2");
        assertThat(PublicInput.bounds(scientific).west()).isEqualTo(126.9);
        var p=bounds(); p.set("east","127.150000000000001");
        assertThatThrownBy(()->PublicInput.bounds(p)).isInstanceOfSatisfying(PublicInput.Failure.class,e->assertThat(e.status).isEqualTo(422));
    }
    @Test void rejectsDuplicateUnknownMissingInvalidAndNonFiniteValues() {
        for (String bad : new String[]{"NaN","Infinity","-Infinity","0x1.0p1","1e2",""," 127","181","126.8"}) {
            var p=bounds(); p.set("east",bad); assertThatThrownBy(()->PublicInput.bounds(p)).isInstanceOf(PublicInput.Failure.class);
        }
        var p=bounds(); p.add("west","126.91"); assertThatThrownBy(()->PublicInput.bounds(p)).isInstanceOf(PublicInput.Failure.class);
        var missing=bounds(); missing.remove("north"); assertThatThrownBy(()->PublicInput.bounds(missing)).isInstanceOf(PublicInput.Failure.class);
        var extra=bounds(); extra.add("extra","1"); assertThatThrownBy(()->PublicInput.bounds(extra)).isInstanceOf(PublicInput.Failure.class);
    }
    @Test void searchAndPageContracts() {
        var p=new LinkedMultiValueMap<String,String>(); p.add("query","  한😀  ");
        assertThat(PublicInput.page(p,false).query()).isEqualTo("한😀");
        p.set("query","😀".repeat(101)); assertThatThrownBy(()->PublicInput.page(p,false)).isInstanceOf(PublicInput.Failure.class);
        p.clear(); p.add("sourceSize","101"); assertThatThrownBy(()->PublicInput.page(p,true)).isInstanceOf(PublicInput.Failure.class);
    }
}
