/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS
 * FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 */
package com.guillermomolina.protos.runtime;
 import java.util.*;
public final class ProtosMapValue extends ProtosObjectValue {
 public static final class Entry { private final Object key; private final ProtosNumericHashKey hash; private Object value;
  Entry(Object k,ProtosNumericHashKey h,Object v){key=Objects.requireNonNull(k);hash=Objects.requireNonNull(h);value=Objects.requireNonNull(v);}
  public Object key(){return key;} public ProtosNumericHashKey recordedHash(){return hash;} public Object value(){return value;} void value(Object v){value=Objects.requireNonNull(v);}
 }
 private static final class Bucket {
  private final List<Entry> entries=new ArrayList<>();
  private final List<Entry> view=Collections.unmodifiableList(entries);
 }
 private final List<Entry> entries=new ArrayList<>();
 private final Map<ProtosNumericHashKey,Bucket> entriesByHash=new HashMap<>();
 private int comparisonDepth;
 public ProtosMapValue(Object parent){super(parent);} public int keyedSize(){return entries.size();}
 public List<Entry> keyedSnapshot(){return List.copyOf(entries);}
 public List<Entry> candidatesForRecordedHash(ProtosNumericHashKey hash){
  Bucket bucket=entriesByHash.get(Objects.requireNonNull(hash));
  return bucket==null?List.of():bucket.view;
 }
 public List<Map.Entry<Object,Object>> associationSnapshot(){ArrayList<Map.Entry<Object,Object>> snapshot=new ArrayList<>(entries.size());for(int index=0;index<entries.size();index++){Entry entry=entries.get(index);snapshot.add(Map.entry(entry.key(),entry.value()));}return List.copyOf(snapshot);} public boolean comparisonActive(){return comparisonDepth!=0;}
 public void enterComparison(){comparisonDepth++;} public void leaveComparison(){if(comparisonDepth<=0)throw new IllegalStateException("unbalanced Map comparison scope");comparisonDepth--;}
 public void append(Object k,ProtosNumericHashKey h,Object v){
  Entry entry=new Entry(k,h,v);
  entries.add(entry);
  entriesByHash.computeIfAbsent(entry.recordedHash(),ignored->new Bucket()).entries.add(entry);
 }
 public void replaceValue(Entry e,Object v){e.value(v);}
 public Object remove(Entry e){
  int insertionIndex=entries.indexOf(e);
  Bucket bucket=entriesByHash.get(e.recordedHash());
  if(insertionIndex<0||bucket==null||!bucket.entries.contains(e))throw new IllegalStateException("foreign Map entry");
  entries.remove(insertionIndex);
  bucket.entries.remove(e);
  if(bucket.entries.isEmpty())entriesByHash.remove(e.recordedHash());
  return e.value();
 }
}
