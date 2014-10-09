#!/usr/bin/env groovy
import groovy.transform.*
import groovy.sql.Sql

import java.nio.file.*

// class for options to make them accessible by class ATColumn
class ATOptions {
	static GUESS_LINES=100
	static DROP_CREATE_TABLE=true
	static STRING_DELIMITER=/(^\"|\"$)/
	static FIELD_SEPARATOR=/;(?=([^\"]*\"[^\"]*\")*[^\"]*$)/    // ignore ; in quotes
	static PROPERTIES="system.properties"
	static DATE_FORMATS=["dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy HH:mm", "dd.MM.yyyy"]
	static SQL_DATE="yyyy-MM-dd HH:mm:ss"
	static PG_STRING_ESCAPE=true  // enable C-Style escapes like /n
}

@Canonical
class ATColumn {	
	String name
	String type // bigint, numeric, text, timestamp
	boolean primary=false
	static String BIGINT="bigint"
	static String NUMERIC="numeric"
	static String TEXT="text"
	static String TIMESTAMP="timestamp"
	
	static void setMysqlTypes() {
		NUMERIC="double"
		ATOptions.PG_STRING_ESCAPE=false
	}
	
	def addTypeSample(String value) {
		value=value.trim().replaceAll(ATOptions.STRING_DELIMITER, "")
		if(value.size()==0) { return }
		def sample=TEXT
		if(value.isNumber()) { sample=NUMERIC }
		if(value.isLong()) { sample=BIGINT }
		else {
			ATOptions.DATE_FORMATS.each {
				try {
					Date.parse(it,value)
					sample=TIMESTAMP
				}
				catch(Exception x) { }
			}
		}
		//println "type: $type - sample: $sample - value: $value"
		
		if(type==null) { type=sample; return }
		if(type==sample) { return }
		if(type==NUMERIC && sample==BIGINT) { return }
		if(type==BIGINT && sample==NUMERIC) { type=NUMERIC; return }
		type=TEXT //type!=sample -> text
	}
	
	def getDDL() {
		return name+" "+type+(primary ? " primary key" : "")
	}
	
	def getSQLValue(String field) {
		if(field.length()==0) { return "null" }
		field=field.trim().replaceAll(ATOptions.STRING_DELIMITER, "")
		if(type==TEXT) {return ATOptions.PG_STRING_ESCAPE ? "E'"+field+"'" : "'"+field+"'" }
		if(type==TIMESTAMP) {
			for(def format: ATOptions.DATE_FORMATS) {
				try {
					def time=Date.parse(format,field)
					return "'"+time.format(ATOptions.SQL_DATE)+"'"
				}
				catch(Exception x) { }
			}
			return "null"
		}
		return field
	}
}


tablename=Paths.get(args[0]).getFileName().toString().tokenize('.')[0]
columns=[]

sysprops=new File("${System.properties.'user.home'}/${ATOptions.PROPERTIES}")
assert sysprops.exists()
println("reading properties from $sysprops")
sysprops.withInputStream {
	System.properties.load(it)
}

println "connecting to database: ${System.properties.'autotable.jdbc.url'} as user ${System.properties.'autotable.user'}"
def sql= Sql.newInstance("${System.properties.'autotable.jdbc.url'}",
					"${System.properties.'autotable.user'}",
					"${System.properties.'autotable.password'}")

if((System.properties.'autotable.jdbc.url').toLowerCase().indexOf("mysql")>4) {
	ATColumn.setMysqlTypes()
}

data=new File(args[0])

// We analyze the first x lines to guess columntypes
line=0;
data.withReader { reader ->
	while((row=reader.readLine())!=null) {
		line++
		row=row.trim()
		if(row.length()==0) { continue }
		if(line>ATOptions.GUESS_LINES) { break }
		def fields=row.split(ATOptions.FIELD_SEPARATOR)
		if(line==1) {
			fields.each { columns << new ATColumn(it,null) }
			columns[0].primary=true
			continue
		}
		[fields, columns].transpose().each { field,column -> column.addTypeSample(field) }
	}
}

if(ATOptions.DROP_CREATE_TABLE) {
	println "drop table $tablename"
	sql.execute("drop table if exists "+tablename)
	command="create table "+tablename
	command+=" (" + columns*.getDDL().join(",") + ")"
	println command
	sql.execute(command)	
}


// read data
line=0
inserts=0
data.splitEachLine(ATOptions.FIELD_SEPARATOR) { fields ->
	line++
	if(line>1 && fields.size()>1) {
		command="insert into "+tablename
		command+=" (" + columns*.name.join(",") + ") values "
		vallist=[]
		[fields, columns].transpose().each { field,column -> vallist << column.getSQLValue(field) }
		command+=" (" + vallist.join(",") + ")"
		//println command
		sql.execute(command)
		inserts++
	}
}
println "== total lines: $line == inserts: $inserts =="
sql.close()