/*
 * Copyright 2019 Maksim Zheravin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package exchange.core2.core.art;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

@Slf4j
public class LongAdaptiveRadixTreeMapTest {

    private LongAdaptiveRadixTreeMap<String> map;
    private TreeMap<Long, String> origMap;

    @Before
    public void before() {
        map = new LongAdaptiveRadixTreeMap<>();
        origMap = new TreeMap<>();
    }

    @Test
    public void shouldPerformBasicOperations() {

        map.validateInternalState();
        assertNull(map.get(0));
        map.put(2, "two");
        map.validateInternalState();
        //assertThat(map.get(2), is("two"));
        map.put(223, "dds");
        map.put(49, "fn");
        map.put(1, "fn");

//        System.out.println(String.format("11239847219L = %016X", 11239847219L));
//        System.out.println(String.format("1123909L = %016X", 1123909L));
        map.put(Long.MAX_VALUE, "fn");
        map.put(11239847219L, "11239847219L");
        map.put(1123909L, "1123909L");
        map.put(11239837212L, "11239837212L");
        map.put(13213, "13213");
        map.put(13423, "13423");

//        System.out.println(map.printDiagram());

        assertThat(map.get(223), is("dds"));
        assertThat(map.get(Long.MAX_VALUE), is("fn"));
        assertThat(map.get(11239837212L), is("11239837212L"));


//        System.out.println(map.printDiagram());

    }

    @Test
    public void shouldCallForEach() {
        map.put(533, "533");
        map.put(573, "573");
        map.put(38234, "38234");
        map.put(38251, "38251");
        map.put(38255, "38255");
        map.put(40001, "40001");
        map.put(40021, "40021");
        map.put(40023, "40023");
//        System.out.println(map.printDiagram());
        final List<Long> keys = new ArrayList<>();
        final List<String> values = new ArrayList<>();
        final LongObjConsumer<String> consumer = (k, v) -> {
            keys.add(k);
            values.add(v);
        };
        Long[] keysArray = {533L, 573L, 38234L, 38251L, 38255L, 40001L, 40021L, 40023L};
        String[] valuesArray = {"533", "573", "38234", "38251", "38255", "40001", "40021", "40023"};
        List<Long> keysExpected = Arrays.asList(keysArray);
        List<String> valuesExpected = Arrays.asList(valuesArray);
        List<Long> keysExpectedRev = Arrays.asList(Arrays.copyOf(keysArray, keysArray.length));
        List<String> valuesExpectedRev = Arrays.asList(Arrays.copyOf(valuesArray, valuesArray.length));
        Collections.reverse(keysExpectedRev);
        Collections.reverse(valuesExpectedRev);

        map.forEach(consumer, Integer.MAX_VALUE);
        assertThat(keys, is(keysExpected));
        assertThat(values, is(valuesExpected));
        keys.clear();
        values.clear();

        map.forEach(consumer, 8);
        assertThat(keys, is(keysExpected));
        assertThat(values, is(valuesExpected));
        keys.clear();
        values.clear();

        map.forEach(consumer, 3);
        assertThat(keys, is(keysExpected.subList(0, 3)));
        assertThat(values, is(valuesExpected.subList(0, 3)));
        keys.clear();
        values.clear();

        map.forEach(consumer, 0);
        assertThat(keys, is(Collections.emptyList()));
        assertThat(values, is(Collections.emptyList()));
        keys.clear();
        values.clear();


        map.forEachDesc(consumer, Integer.MAX_VALUE);
        assertThat(keys, is(keysExpectedRev));
        assertThat(values, is(valuesExpectedRev));
        keys.clear();
        values.clear();

        map.forEachDesc(consumer, 8);
        assertThat(keys, is(keysExpectedRev));
        assertThat(values, is(valuesExpectedRev));
        keys.clear();
        values.clear();

        map.forEachDesc(consumer, 3);
        assertThat(keys, is(keysExpectedRev.subList(0, 3)));
        assertThat(values, is(valuesExpectedRev.subList(0, 3)));
        keys.clear();
        values.clear();

        map.forEachDesc(consumer, 0);
        assertThat(keys, is(Collections.emptyList()));
        assertThat(values, is(Collections.emptyList()));
        keys.clear();
        values.clear();
    }

    @Test
    public void shouldFindHigherKeys() {

        map.put(33, "33");
        map.put(273, "273");
        map.put(182736400230L, "182736400230");
        map.put(182736487234L, "182736487234");
        map.put(37, "37");
        System.out.println(map.printDiagram());

//        assertThat(map.getHigherValue(63120L), is(String.valueOf(182736400230L)));
//        assertThat(map.getHigherValue(255), is(String.valueOf("273")));
//
        for (int x = 37; x < 273; x++) {
//            log.debug("TRY:{} {}", x, String.format("%Xh", x));
            assertThat(map.getHigherValue(x), is("273"));
        }
//
        for (int x = 273; x < 100000; x++) {
//            log.debug("TRY:{} {}", x, String.format("%Xh", x));
            assertThat(map.getHigherValue(x), is("182736400230"));
        }
//            log.debug("TRY:{} {}", 182736388198L, String.format("%Xh", 182736388198L));
        assertThat(map.getHigherValue(182736388198L), is("182736400230"));

        for (long x = 182736300230L; x < 182736400229L; x++) {
//            log.debug("TRY:{} {}", x, String.format("%Xh", x));
            assertThat(map.getHigherValue(x), is("182736400230"));
        }
        for (long x = 182736400230L; x < 182736487234L; x++) {
//            log.debug("TRY:{} {}", x, String.format("%Xh", x));
            assertThat(map.getHigherValue(x), is("182736487234"));
        }
        for (long x = 182736487234L; x < 182736497234L; x++) {
//            log.debug("TRY:{} {}", x, String.format("%Xh", x));
            assertNull(map.getHigherValue(x));
        }

    }

    @Test
    public void shouldFindLowerKeys() {

        map.put(33, "33");
        map.put(273, "273");
        map.put(182736400230L, "182736400230");
        map.put(182736487234L, "182736487234");
        map.put(37, "37");
        System.out.println(map.printDiagram());

        assertThat(map.getLowerValue(63120L), is(String.valueOf(273L)));
        assertThat(map.getLowerValue(255), is(String.valueOf("37")));
        assertThat(map.getLowerValue(275), is(String.valueOf("273")));

        assertNull(map.getLowerValue(33));
        assertNull(map.getLowerValue(32));
        for (int x = 34; x <= 37; x++) {
//            log.debug("TRY:{} {}", x, String.format("%Xh", x));
            assertThat(map.getLowerValue(x), is("33"));
        }
        for (int x = 38; x <= 273; x++) {
//            log.debug("TRY:{} {}", x, String.format("%Xh", x));
            assertThat(map.getLowerValue(x), is("37"));
        }
//
        for (int x = 274; x < 100000; x++) {
//            log.debug("TRY:{} {}", x, String.format("%Xh", x));
            assertThat(map.getLowerValue(x), is("273"));
        }

//            log.debug("TRY:{} {}", 182736388198L, String.format("%Xh", 182736388198L));
        assertThat(map.getLowerValue(182736487334L), is("182736487234"));

        for (long x = 182736300230L; x < 182736400230L; x++) {
//            log.debug("TRY:{} {}", x, String.format("%Xh", x));
            assertThat(map.getLowerValue(x), is("273"));
        }
//        for (long x = 182736400230L; x < 182736487234L; x++) {
////            log.debug("TRY:{} {}", x, String.format("%Xh", x));
//            assertThat(map.getHigherValue(x), is("182736487234"));
//        }
//        for (long x = 182736487234L; x < 182736497234L; x++) {
////            log.debug("TRY:{} {}", x, String.format("%Xh", x));
//            assertNull(map.getHigherValue(x));
//        }

    }


    @Test
    public void shouldCompactNodes() {
        put(2, "2");
        System.out.println(map.printDiagram());
        assertThat(map.get(2), is("2"));
        assertNull(map.get(3));
        assertNull(map.get(256 + 2));
        assertNull(map.get(256 * 256 * 256 + 2));
        assertNull(map.get(Long.MAX_VALUE - 0xFF + 2));

//        map.put(0x010002L, "0x010002");
//        map.put(0xFF0002L, "0xFF0002");
//        map.put(Long.MAX_VALUE, "MAX_VALUE");
        put(0x414F32L, "0x414F32");
        put(0x414F33L, "0x414F33");
        put(0x414E00L, "0x414E00");
        put(0x407654L, "0x407654");
        put(0x33558822DD44AA11L, "0x33558822DD44AA11");
        put(0xFFFFFFFFFFFFFFL, "0xFFFFFFFFFFFFFF");
        put(0xFFFFFFFFFFFFFEL, "0xFFFFFFFFFFFFFE");
        put(0x112233445566L, "0x112233445566");
        put(0x1122AAEE5566L, "0x1122AAEE5566");
        System.out.println(map.printDiagram());

        assertThat(map.get(0x414F32L), is("0x414F32"));
        assertThat(map.get(0x414F33L), is("0x414F33"));
        assertThat(map.get(0x414E00L), is("0x414E00"));
        assertNull(map.get(0x414D00L));
        assertNull(map.get(0x414D33L));
        assertNull(map.get(0x414D32L));
        assertNull(map.get(0x424F32L));

        assertThat(map.get(0x407654L), is("0x407654"));
        assertThat(map.get(0x33558822DD44AA11L), is("0x33558822DD44AA11"));
        assertNull(map.get(0x00558822DD44AA11L));
        assertThat(map.get(0xFFFFFFFFFFFFFFL), is("0xFFFFFFFFFFFFFF"));
        assertThat(map.get(0xFFFFFFFFFFFFFEL), is("0xFFFFFFFFFFFFFE"));
        assertNull(map.get(0xFFFFL));
        assertNull(map.get(0xFFL));
        assertThat(map.get(0x112233445566L), is("0x112233445566"));
        assertThat(map.get(0x1122AAEE5566L), is("0x1122AAEE5566"));
        assertNull(map.get(0x112333445566L));
        assertNull(map.get(0x112255445566L));
        assertNull(map.get(0x112233EE5566L));
        assertNull(map.get(0x1122AA445566L));

//        map.remove(0x112233445566L);
        remove(0x414F32L);
        remove(0x414E00L);
        remove(0x407654L);
        remove(2);
        System.out.println(map.printDiagram());

    }

    @Test
    public void shouldExtendTo16andReduceTo4() {
        put(2, "2");
        put(223, "223");
        put(49, "49");
        put(1, "1");
        // 4->16
        put(77, "77");
        put(4, "4");

        remove(223);
        remove(1);
        // 16->4
        remove(4);
        remove(49);

        // reduce intermediate

        put(65536 * 7, "65536*7");
        put(65536 * 3, "65536*3");
        put(65536 * 2, "65536*2");
        // 4->16
        put(65536 * 4, "65536*4");
        put(65536 * 3 + 3, "65536*3+3");

        remove(65536 * 2);
        // 16->4
        remove(65536 * 4);
        remove(65536 * 7);
//        System.out.println(map.printDiagram());
    }

    @Test
    public void shouldExtendTo48andReduceTo16() {
        // reduce at end level

        for (int i = 0; i < 16; i++) {
            put(i, "" + i);
        }
        // 16->48
        put(177, "177");
        put(56, "56");
        put(255, "255");

        remove(0);
        remove(16);
        remove(13);
        remove(17); // nothing
        remove(3);
        remove(5);
        remove(255);
        remove(7);
        // 48->16
        remove(8);
        remove(2);
        remove(38);
        put(4, "4A");


        // reduce intermediate

        for (int i = 0; i < 16; i++) {
            put(256 * i, "" + 256 * i);
        }


        // 16->48
        put(256 * 47, "" + 256 * 47);
        put(256 * 27, "" + 256 * 27);
        put(256 * 255, "" + 256 * 255);
        put(256 * 22, "" + 256 * 22);


        remove(256 * 5);
        remove(256 * 6);
        remove(256 * 7);
        remove(256 * 8);
        remove(256 * 9);
        remove(256 * 10);
        remove(256 * 11);
        // 48->16
        remove(256 * 15);
        remove(256 * 13);
        remove(256 * 14);
        remove(256 * 12);

//        System.out.println(map.printDiagram());
    }


    @Test
    public void shouldExtendTo256andReduceTo48() {
        // reduce at end level
        for (int i = 0; i < 48; i++) {
            int key = 255 - i * 3;
            put(key, "" + key);
        }

//        // 48->256
        put(176, "176");
        put(221, "221");

        remove(252);
        remove(132);
        remove(135);
        remove(138);
        remove(141);
        remove(144);
        remove(147);
        remove(150);
        remove(153);
        remove(156);
        remove(159);
        remove(162);
        remove(165);

        for (int i = 0; i < 50; i++) {
            int key = 65536 * (13 + i * 3);
            put(key, "" + key);
        }

        for (int i = 10; i < 30; i++) {
            int key = 65536 * (13 + i * 3);
            remove(key);
        }

        System.out.println(map.printDiagram());
    }

    @Test
    public void shouldLoadManyItems() {
        List<Long> bstPutTime = new ArrayList<>();
        List<Long> artPutTime = new ArrayList<>();
        List<Long> bstGetHitTime = new ArrayList<>();
        List<Long> artGetHitTime = new ArrayList<>();
        List<Long> bstRemoveTime = new ArrayList<>();
        List<Long> artRemoveTime = new ArrayList<>();

        Random rand = new Random(1);

        final UnaryOperator<Integer> stepFunction = i -> 1 + rand.nextInt((int) Math.min(Integer.MAX_VALUE, 1L + (Long.highestOneBit(i) >> 8)));
//        final UnaryOperator<Integer> stepFunction = i->1;
//        final UnaryOperator<Integer> stepFunction = i->1+rand.nextInt(Integer.MAX_VALUE);

        long timeEnd = System.currentTimeMillis() + 2_000;
        for (int iter = 0; System.currentTimeMillis() < timeEnd && iter < 1000; iter++) {
            log.debug("-------------------iteration:{} ({}s left)------------------------", iter, (timeEnd - System.currentTimeMillis()) / 1000);

            LongAdaptiveRadixTreeMap<Long> art = new LongAdaptiveRadixTreeMap<>();

            TreeMap<Long, Long> bst = new TreeMap<>();

//            int num = 500_000;
            int num = 100_000;
            List<Long> list = new ArrayList<>(num);
            long j = 0;
            log.debug("generate random numbers..");
            for (int i = 0; i < num; i++) {
                list.add(1_000_000_000L + j);
                j += stepFunction.apply(i);
            }
            log.debug("shuffle..");
            Collections.shuffle(list, rand);

            log.debug("put into BST..");
            long t = System.nanoTime();
            list.forEach(x -> bst.put(x, x));
            long bstPutTimeNs = System.nanoTime() - t;
            log.debug("put into ADT..");
//            list.forEach(x -> log.debug("{}", x));

            t = System.nanoTime();
            list.forEach(x -> art.put(x, x));
            long artPutTimeNs = System.nanoTime() - t;

            log.debug("shuffle..");
            Collections.shuffle(list, rand);

            log.debug("get (hit) from BST..");
            t = System.nanoTime();
            long sum = 0;
            for (long x : list) {
                sum += bst.get(x);
            }
            long bstGetHitTimeNs = System.nanoTime() - t;

            log.debug("get (hit) from ADT..");
            t = System.nanoTime();
            for (long x : list) {
                sum += art.get(x);
            }
            long artGetHitTimeNs = System.nanoTime() - t;
            log.debug("done ({})", sum);

            //        log.debug("\n{}", adt.printDiagram());
            log.debug("validating..");
            art.validateInternalState();
            checkStreamsEqual(art.entriesList().stream(), bst.entrySet().stream());

            log.debug("shuffle again..");
            Collections.shuffle(list, rand);


            log.debug("validate getHigherValue method..");
            for (long x : list) {
//                log.debug("CHECK:{} {} ---------", x, String.format("%Xh", x));
                Long v1 = art.getHigherValue(x);
                Map.Entry<Long, Long> entry = bst.higherEntry(x);
                Long v2 = entry != null ? entry.getValue() : null;
                if (!Objects.equals(v1, v2)) {
                    log.debug("ART  :{} {}", v1, String.format("%Xh", v1));
                    log.debug("BST  :{} {}", v2, String.format("%Xh", v2));
                    System.out.println(art.printDiagram());
                    throw new IllegalStateException();
                }

//                assertThat(v1, is(v2));
            }

            log.debug("validate getLowerValue method..");
            for (long x : list) {
//                log.debug("CHECK:{} {} ---------", x, String.format("%Xh", x));
                Long v1 = art.getLowerValue(x);
                Map.Entry<Long, Long> entry = bst.lowerEntry(x);
                Long v2 = entry != null ? entry.getValue() : null;
                if (!Objects.equals(v1, v2)) {
                    log.debug("ART  :{} {}", v1, String.format("%Xh", v1));
                    log.debug("BST  :{} {}", v2, String.format("%Xh", v2));
                    System.out.println(art.printDiagram());
                    throw new IllegalStateException();
                }

//                assertThat(v1, is(v2));
            }

            log.debug("remove from BST..");
            t = System.nanoTime();
            list.forEach(bst::remove);
            long bstRemoveTimeNs = System.nanoTime() - t;

            log.debug("remove from ADT..");
//        list.forEach(x -> {
////            log.debug("\n{}", adt.printDiagram());
//            adt.validateInternalState();
//            log.debug("REMOVING {}", x);
//            adt.remove(x);
//        });
            t = System.nanoTime();
            list.forEach(art::remove);
            long artRemoveTimeNs = System.nanoTime() - t;

            log.debug("validating..");
            art.validateInternalState();
            checkStreamsEqual(art.entriesList().stream(), bst.entrySet().stream());

            if (iter > 1) {
                bstPutTime.add(bstPutTimeNs);
                artPutTime.add(artPutTimeNs);
                bstGetHitTime.add(bstGetHitTimeNs);
                artGetHitTime.add(artGetHitTimeNs);
                bstRemoveTime.add(bstRemoveTimeNs);
                artRemoveTime.add(artRemoveTimeNs);

                long bstPutTimeNsAvg = Math.round(bstPutTime.stream().mapToLong(x -> x).average().orElse(0));
                long artPutTimeNsAvg = Math.round(artPutTime.stream().mapToLong(x -> x).average().orElse(0));
                long bstGetHitTimeNsAvg = Math.round(bstGetHitTime.stream().mapToLong(x -> x).average().orElse(0));
                long artGetHitTimeNsAvg = Math.round(artGetHitTime.stream().mapToLong(x -> x).average().orElse(0));
                long bstRemoveTimeNsAvg = Math.round(bstRemoveTime.stream().mapToLong(x -> x).average().orElse(0));
                long artRemoveTimeNsAvg = Math.round(artRemoveTime.stream().mapToLong(x -> x).average().orElse(0));

                log.info("AVERAGE PUT    BST {}ms ADT {}ms ({}%)",
                        nanoToMs(bstPutTimeNsAvg), nanoToMs(artPutTimeNsAvg), percentImprovement(bstPutTimeNsAvg, artPutTimeNsAvg));

                log.info("AVERAGE GETHIT BST {}ms ADT {}ms ({}%)",
                        nanoToMs(bstGetHitTimeNsAvg), nanoToMs(artGetHitTimeNsAvg), percentImprovement(bstGetHitTimeNsAvg, artGetHitTimeNsAvg));

                log.info("AVERAGE REMOVE BST {}ms ADT {}ms ({}%)",
                        nanoToMs(bstRemoveTimeNsAvg), nanoToMs(artRemoveTimeNsAvg), percentImprovement(bstRemoveTimeNsAvg, artRemoveTimeNsAvg));
            }
        }

        log.info("---------------------------------------");


    }

    private static float nanoToMs(long nano) {
        return ((float) (nano / 1000)) / 1000f;
    }

    private static float percentImprovement(long oldTime, long newTime) {
        return 100f * ((float) oldTime / (float) newTime - 1f);
    }


    private void put(long key, String value) {
        map.put(key, value);
        map.validateInternalState();
        origMap.put(key, value);

//        map.entriesList().forEach(entry -> System.out.println("k=" + entry.getKey() + " v=" + entry.getValue()));
//        origMap.forEach((key1, value1) -> System.out.println("k1=" + key1 + " v1=" + value1));
//        System.out.println(map.printDiagram());

        checkStreamsEqual(map.entriesList().stream(), origMap.entrySet().stream());
    }

    private void remove(long key) {
        map.remove(key);
        map.validateInternalState();
        origMap.remove(key);

//        map.entriesList().forEach(entry -> System.out.println("k=" + entry.getKey() + " v=" + entry.getValue()));
//        origMap.forEach((key1, value1) -> System.out.println("k1=" + key1 + " v1=" + value1));

        checkStreamsEqual(map.entriesList().stream(), origMap.entrySet().stream());
    }


    private static <K, V> void checkStreamsEqual(final Stream<Map.Entry<K, V>> entry, final Stream<Map.Entry<K, V>> origEntry) {
        final Iterator<Map.Entry<K, V>> iter = entry.iterator();
        final Iterator<Map.Entry<K, V>> origIter = origEntry.iterator();
        while (iter.hasNext() && origIter.hasNext()) {
            final Map.Entry<K, V> next = iter.next();
            final Map.Entry<K, V> origNext = origIter.next();
            if (!next.getKey().equals(origNext.getKey())) {
                throw new IllegalStateException(String.format("unexpected key: %s  (expected %s)", next.getKey(), origNext.getKey()));
            }
            if (!next.getValue().equals(origNext.getValue())) {
                throw new IllegalStateException(String.format("unexpected value: %s  (expected %s)", next.getValue(), origNext.getValue()));
            }
        }
        if (iter.hasNext() || origIter.hasNext()) {
            throw new IllegalStateException("different size");
        }
    }


}