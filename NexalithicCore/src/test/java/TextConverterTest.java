import com.thezeroer.nexalithic.core.util.TextConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文本转换器测试
 *
 * @author tbrtz647@outlook.com
 * @since 2026/04/16
 * @version 1.0.0
 */
@DisplayName("NTP 文本转换器集成测试")
class TextConverterTest {

    // --- Collection 测试 ---

    @Test
    @DisplayName("List 序列化与反序列化测试")
    void testListConversion() {
        List<String> original = Arrays.asList("apple", "banana", "cherry");
        String ntp = TextConverter.fromList(original);
        
        assertEquals("[\"apple\",\"banana\",\"cherry\"]", ntp);
        
        List<String> result = TextConverter.toList(ntp);
        assertEquals(original, result);
    }

    @Test
    @DisplayName("Set 序列化与去重反序列化测试")
    void testSetConversion() {
        Set<String> original = new LinkedHashSet<>(Arrays.asList("A", "B", "A"));
        String ntp = TextConverter.fromSet(original);
        
        // 注意：Set 序列化后应该是 ["A","B"]
        assertEquals("[\"A\",\"B\"]", ntp);
        
        Set<String> result = TextConverter.toSet(ntp);
        assertEquals(2, result.size());
        assertTrue(result.contains("A") && result.contains("B"));
    }

    // --- Map 测试 ---

    @Test
    @DisplayName("Map 序列化与反序列化测试")
    void testMapConversion() {
        Map<String, String> original = new LinkedHashMap<>();
        original.put("name", "Gemini");
        original.put("role", "AI");
        
        String ntp = TextConverter.fromMap(original);
        assertEquals("{\"name\":\"Gemini\",\"role\":\"AI\"}", ntp);
        
        Map<String, String> result = TextConverter.toMap(ntp);
        assertEquals(original, result);
    }

    // --- 二维结构测试 ---

    @Test
    @DisplayName("ListArray (二维表格) 序列化测试")
    void testListArrayConversion() {
        List<String[]> original = new ArrayList<>();
        original.add(new String[]{"r1c1", "r1c2"});
        original.add(new String[]{"r2c1", "r2c2"});
        
        String ntp = TextConverter.fromListArray(original);
        // 期望格式: ["r1c1","r1c2"];["r2c1","r2c2"]
        assertEquals("[\"r1c1\",\"r1c2\"];[\"r2c1\",\"r2c2\"]", ntp);
        
        List<String[]> result = TextConverter.toListArray(ntp);
        assertEquals(2, result.size());
        assertArrayEquals(original.get(0), result.get(0));
        assertArrayEquals(original.get(1), result.get(1));
    }

    // --- 核心：转义与特殊字符测试 ---

    @Test
    @DisplayName("特殊字符转义全覆盖测试 (引号、反斜杠、分号)")
    void testComplexEscaping() {
        List<String> complexList = Arrays.asList(
                "quote\"",       // 包含引号
                "slash\\",       // 包含反斜杠
                "semicolon;",    // 包含分号 (二维分隔符)
                "mix\\\";",      // 混合
                ""               // 空字符串
        );
        
        String ntp = TextConverter.fromList(complexList);
        List<String> result = TextConverter.toList(ntp);
        
        assertEquals(complexList.size(), result.size());
        for (int i = 0; i < complexList.size(); i++) {
            assertEquals(complexList.get(i), result.get(i), "第 " + i + " 个元素转义匹配失败");
        }
    }

    @Test
    @DisplayName("双重转义边界测试 (\\\\;)")
    void testDoubleEscape() {
        // 测试输入包含 "\\;" 这种情况
        String special = "back\\\\;slash"; 
        List<String> list = Collections.singletonList(special);
        
        String ntp = TextConverter.fromList(list);
        List<String> result = TextConverter.toList(ntp);
        
        assertEquals(special, result.get(0));
    }

    // --- 边界情况测试 ---

    @Test
    @DisplayName("空值与 Null 的鲁棒性测试")
    void testEmptyAndNull() {
        // 1. 空集合
        assertEquals("[]", TextConverter.fromCollection(Collections.emptyList()));
        assertEquals("{}", TextConverter.fromMap(Collections.emptyMap()));
        assertEquals("", TextConverter.fromListArray(Collections.emptyList()));

        // 2. 元素包含 null (应转为空字符串)
        List<String> listWithNull = Collections.singletonList(null);
        assertEquals("[\"\"]", TextConverter.fromList(listWithNull));

        // 3. 输入非法字符串
        assertTrue(TextConverter.toList("").isEmpty());
        assertTrue(TextConverter.toList(null).isEmpty());
        assertTrue(TextConverter.toMap("invalid").isEmpty());
    }

    @Test
    @DisplayName("万能容器 Supplier 测试")
    void testSupplier() {
        String data = "[\"1\",\"2\",\"1\"]";
        
        // 测试转为 TreeSet (自动排序且去重)
        TreeSet<String> treeSet = TextConverter.toCollection(data, TreeSet::new);
        assertEquals(2, treeSet.size());
        assertEquals("1", treeSet.first());
        
        // 测试转为 LinkedList
        LinkedList<String> linkedList = TextConverter.toCollection(data, LinkedList::new);
        assertEquals(3, linkedList.size());
        assertEquals("1", linkedList.peekFirst());
    }
}