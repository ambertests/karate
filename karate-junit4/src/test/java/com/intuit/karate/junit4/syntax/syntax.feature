@ignore
Feature: karate syntax demo

Background:

Scenario: syntax examples

# check if auto config worked from classpath:karate-config.js
# and system property access worked
* assert env == 'foo'
* assert testConfig == 'bar'
* print 'hello world'

* def a = 1
* def b = 2
* def c = a + b
* assert c == 3

# json is a first class citizen, keys don't need to be quoted and single-quotes are fine
* def myJson = { foo: 'bar' }

# assert json path expressions
* match myJson $.foo == 'bar'
# prefer the shorthand for match
* match myJson.foo == 'bar'
* match myJson == { foo: 'bar' }

# save chunks of json to memory
* def parent = { foo: 'bar', 'ban': { a: 1 } }
* def child = parent.ban
* match child.a == 1

# json array access
* def parent = { foo: 'bar', 'ban': { a: [1, 2, 3] } }
* def child = parent.ban
* match child.a[1] == 2

# manipulate json documents
* def myJson = { foo: 'bar' }
* set myJson.foo = 'world'
* match myJson == { foo: 'world' }

# add new keys
* set myJson $.hey = 'ho'
* match myJson == { foo: 'world', hey: 'ho' }

# and even append to json arrays (or create them automatically)
* set myJson.zee[0] = 5
* match myJson == { foo: 'world', hey: 'ho', zee: [5] }

# json chunks
* set myJson.cat = { name: 'Billie' }
* match myJson == { foo: 'world', hey: 'ho', zee: [5], cat: { name: 'Billie' } }

# and the order of keys does not matter
* match myJson == { cat: { name: 'Billie' }, hey: 'ho', foo: 'world', zee: [5] }

# you can ignore fields marked as #ignore
* match myJson == { cat: '#ignore', hey: 'ho', foo: 'world', zee: [5] }

# xml is also a first class citizen of the syntax
Given def myXml = <hello>world</hello>
# assert xpath expressions
Then match myXml/hello == 'world'

# xml set
Given def cat = <cat><name>Billie</name></cat>
# with xpath separate
When set cat /cat/name = 'Jean'
Then match cat / == <cat><name>Jean</name></cat>

# with variable + xpath together
* set cat/cat/name = 'King'
* match cat / == <cat><name>King</name></cat>

# set xml chunks
* def xml = <foo><bar>baz</bar></foo>
* set xml/foo/bar = <hello>world</hello>
* match xml == <foo><hello>world</hello></foo>

# assign xpath expressions to variables
# also note the multi-line option / syntax
* def myXml =
"""
<root>
  <EntityId>a9f7a56b-8d5c-455c-9d13-808461d17b91</EntityId>
  <Name>test.pdf</Name>
  <Size>100250</Size>
  <Created>2016-12-26 03:36:17.666 PST</Created>
  <Properties/>
</root>
"""
* def documentId = myXml/root/EntityId
* assert documentId == 'a9f7a56b-8d5c-455c-9d13-808461d17b91'

# more xml
* def cat = <cat><name>Billie</name><scores><score>2</score><score>5</score></scores></cat>
# sadly, xpath list indexes start from 1
* match cat/cat/scores/score[2] == '5'
# but karate allows you to traverse xml like json
* match cat.cat.scores.score[1] == 5

# functions !
* def adder = function(a, b) { return a + b }
* assert adder(1, 2) == 3

* def greeter = function(name) { return 'hello ' + name }
* assert greeter('Bob') == 'hello Bob'

# functions can use path notation
* def ticket = { userId: '123456' }  
* assert ticket.userId == '123456'
# match is preferred though because it does smart matching for non-primitives
* match ticket $.userId == '123456'
# even xml can be traversed within js functions but via dot notation (not xpath)
* def moreXml = <foo>bar</foo>
* assert moreXml.foo == 'bar'

# calling java classes
* def dateStringToLong =
"""
function(s) {
  var SimpleDateFormat = Java.type("java.text.SimpleDateFormat");
  var sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
  return sdf.parse(s).time;
} 
"""
* assert dateStringToLong("2016-12-24T03:39:21.081+0000") == 1482550761081

# function parameters can use dot notation to grab nested data from variables that are json documents
* def ticket = { issued: '2016-12-24T03:39:21.081+0000' }
* assert dateStringToLong(ticket.issued) == 1482550761081

# call java code lying anywhere on the classpath, in this example a test class alongside this file
* def doWork =
"""
function(arg) {
  var JavaDemo = Java.type('com.intuit.karate.junit4.syntax.JavaDemo');
  var jd = new JavaDemo();
  return jd.doWork(arg);
}
"""
* def result = call doWork 'world'
* assert result.someKey == 'hello world'

# seamless java interop !
* def JavaDemo = Java.type('com.intuit.karate.junit4.syntax.JavaDemo')
* def result = JavaDemo.doWorkStatic('world')
* assert result == 'hello world'

# calling custom special functions
* def myArg = { foo: 'bar' }
# a function can create new variables if it returns an object
# and the returned object keys will be used as names
* def myFn = function() { return { newArg: myArg.foo + 5 }}
# actual invoke - this is how you can reuse complex logic
* call myFn
* assert newArg == 'bar5'

# you can assign the results of a function call
* def myFn = function() { return myArg.foo + 5 }
* def temp = call myFn
* assert temp == 'bar5'

# functions can take a single argument (primitive or json)
* def myFn = function(a) { return { newArg: a }}
* call myFn 'foo'
* assert newArg == 'foo'
* def anotherFn = function(a) { return { anotherArg: a.hello }}
* call anotherFn { hello: 'world' }
* assert anotherArg == 'world'

# replacing text in strings - graphql filter clause injection example
# in real life this line would read from a file
* def query = 'query q { company { taxAgencies { } } }'
* def replacer = 
"""
function(args) {
  var query = args.query;
  karate.log('before replacement: ', query);
  // the RegExp object is standard JavaScript
  var regex = new RegExp('\\s' + args.field + '\\s*{');
  karate.log('regex: ', regex);
  query = query.replace(regex, ' ' + args.field + '(' + args.criteria + ') {');
  karate.log('after replacement: ', query);
  return query; 
} 
"""
* def query = call replacer { query: '#(query)', field: 'taxAgencies', criteria: 'first: 5' }
* assert query == 'query q { company { taxAgencies(first: 5) { } } }'

# called function scripts can read from the file system
* def reader = function() { return karate.read('demo-json.json') }
* def fromFile = call reader
* match fromFile == { from: 'file' }

# short-cuts for the special response variable
Given def response = { name: 'Billie' }

Then match response $ == { name: 'Billie' }
Then match response == { name: 'Billie' }
Then match $ == { name: 'Billie' }

Then match response.name == 'Billie'
Then match response $.name == 'Billie'
Then match $.name == 'Billie'

Given def response = <cat><name>Billie</name></cat>
  
Then match response / == <cat><name>Billie</name></cat>
Then match response/ == <cat><name>Billie</name></cat>
Then match response == <cat><name>Billie</name></cat>
Then match / == <cat><name>Billie</name></cat>    

Then match response /cat/name == 'Billie'
Then match response/cat/name == 'Billie'
Then match /cat/name == 'Billie' 

# validators

Given def cat = { name: 'Billie', type: 'LOL', id: 'a9f7a56b-8d5c-455c-9d13-808461d17b91' }
Then match cat == { name: '#ignore', type: '#regex[A-Z]{3}', id: '#uuid' }
# this will fail
# Then match cat == { name: '#ignore', type: '#regex.{2}', id: '#uuid' }
Then match cat == { name: '#string', type: '#string', id: '#string'}

* def cat = { foo: 1 }
* match cat == { foo: '#number' }

* def cat = { foo: true }
* match cat == { foo: '#boolean' }

* def cat = { foo: [1, 2] }
* match cat == { foo: '#array' }

* def cat = { foo: { bar: 'baz' } }
* match cat == { foo: '#object' }

# schema validation
* def date = { month: 3 }
* match date == { month: '#? _ > 0 && _ < 13' }

# validation and variables
* def date = { month: 3 }
* def min = 1
* def max = 12
* match date == { month: '#? _ >= min && _ <= max' }

# validation and functions !
* def date = { month: 3 }
# * def isMonth = function(v) { return v >= 0 && v <= 12 }
# * match date == { month: '#? isMonth(_)' }
  
# match contains
Given def cat = 
"""
{
  name: 'Billie',
  kittens: [
      { id: 23, name: 'Bob' },
      { id: 42, name: 'Wild' }
  ]
}
"""
# normal 'equality' match. note the wildcard '*' in the JsonPath (returns an array)
Then match cat.kittens[*].id == [23, 42]

# when inspecting a json array, 'contains' just checks if the expected items exist
# and the size and order of the actual array does not matter
Then match cat.kittens[*].id contains 23
Then match cat.kittens[*].id contains [42]
Then match cat.kittens[*].id contains [23, 42]
Then match cat.kittens[*].id contains [42, 23]

# and yes, you can assert against nested objects within JSON arrays !
Then match cat.kittens contains [{ id: 42, name: 'Wild' }, { id: 23, name: 'Bob' }]

# ... and even ignore fields at the same time !
Then match cat.kittens contains { id: 42, name: '#string' }

# get syntax
* def kitnums = get cat.kittens[*].id
* match kitnums == [23, 42]
* def kitnames = get cat $.kittens[*].name
* match kitnames == ['Bob', 'Wild']

# read from file, text match and contains
Given def text = read('demo-text.txt')
Then match text == 'Hello World!'
Then assert text == 'Hello World!'

# contains for strings
Then match text contains 'World'

* def hello = 'Hello World!'
* match hello == 'Hello World!'
* assert hello == 'Hello World!'
* match hello contains 'World'

Given def pdf = read('test.pdf')
Then match pdf == read('test.pdf')

# json and match contains
* def foo = { bar: 1, baz: 'hello', ban: 'world' }
* match foo contains { bar: 1 }
* match foo contains { baz: 'hello' }
* match foo contains { bar:1, baz: 'hello' }
# * match foo == { bar:1, baz: 'hello' }

# match contains only
* def data = { foo: [1, 2, 3] }
* match data.foo contains 1
* match data.foo contains [2]
* match data.foo contains [3, 2]
* match data.foo contains only [3, 2, 1]
* match data.foo contains only [2, 3, 1]
# * match data.foo contains only [2, 3]

# match each
* def data = { foo: [{ bar: 1, baz: 'a' }, { bar: 2, baz: 'b' }, { bar: 3, baz: 'c' }]}
* match each data.foo == { bar: '#number', baz: '#string' }
* match each data.foo contains { bar: '#number' }
* match each data.foo contains { bar: '#? _ != 4' }
* match each data.foo contains { baz: "#? _ != 'z'" }
* def isAbc = function(x) { return x == 'a' || x == 'b' || x == 'c' }
* match each data.foo contains { baz: '#? isAbc(_)' }

# json path on list
* def json = { foo: [{ bar: 1}, {bar: 2}, {bar: 3}]}
* def list = json.foo
* match list[0].bar == 1
* assert list[0].bar == 1

# more json path on list
* def response = read('agencies.json')
* def agencies = $.data.company.taxAgencies.edges
* match agencies[0].node == { id: '#notnull', name: 'Test', agencyAccount: '#object' }
* def agencyAccount = agencies[0].node.agencyAccount
* match agencyAccount == { liabilityAccount: { id: '#notnull', currentBalance: '#notnull' }}
* def currentBalance = agencyAccount.liabilityAccount.currentBalance
* print 'current balance: ' + currentBalance

# data tables to json
* table cats =
    | name | age |
    | Bob  | 2   |
    | Wild | 4   |
    | Nyan | 3   |

* match cats == [{name: 'Bob', age: 2}, {name: 'Wild', age: 4}, {name: 'Nyan', age: 3}]

# json path in js
* def foo = { bar: [{baz: 1}, {baz: 2}, {baz: 3}]}
* def fun = function(){ return karate.get('foo.bar[*].baz') }
* def res = fun()
* match res == [1, 2, 3]

# force type to raw text especially relevant for graph-ql
* text foo =
"""
{ not json }
"""
* match foo == '{ not json }'
* text bar =
"""
{ 
  this is also
  not json
}
"""
* match bar contains 'not json'

# yaml from file
* def foo = read('mutation.yaml')
* print foo
* match foo ==
"""
{
  name: null,
  input: { 
    id: '1',
    subType: { name: 'name', notes: 'notes', deleted: false }    
  },
  output: [
    'id',
    { edge: [{ node: ['id', 'name', 'notes', 'deleted'] }] }
  ]    
}
"""

# inline yaml
* yaml bar =
"""
name: John
input:
  id: 1
  subType: 
    name: 'Smith'
    notes: 'notes'
    deleted: false
output:
  - id
  - edge:
    - node:
      - id
      - name
      - notes
      - deleted
"""
* match bar ==
"""
{
  name: 'John',
  input: { 
    id: 1,
    subType: { name: 'Smith', notes: 'notes', deleted: false }    
  },
  output: [
    'id',
    { edge: [{ node: ['id', 'name', 'notes', 'deleted'] }] }
  ]    
}
"""

# big decimal
* def nums = { profit: -1002.2000000000002 }
* match nums == { profit: -1002.2000000000002 }
* def nums = { profit: -1002.2000000000000 }
* match nums == { profit: -1002.20 }
* def nums = { profit: -1002.20 }
* match nums == { profit: -1002.2000000000000 }
