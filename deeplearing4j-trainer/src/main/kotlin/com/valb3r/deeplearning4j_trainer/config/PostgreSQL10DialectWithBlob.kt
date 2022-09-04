package com.valb3r.deeplearning4j_trainer.config

import org.hibernate.dialect.PostgreSQL10Dialect
import org.hibernate.type.descriptor.sql.BinaryTypeDescriptor
import org.hibernate.type.descriptor.sql.SqlTypeDescriptor
import java.sql.Types

// Similar issue to https://github.com/jhipster/generator-jhipster/issues/1940
class PostgreSQL10DialectWithBlob : PostgreSQL10Dialect() {

    init {
        registerColumnType(Types.BLOB, "bytea")
    }

    override fun remapSqlTypeDescriptor(sqlTypeDescriptor: SqlTypeDescriptor): SqlTypeDescriptor {
        return if (sqlTypeDescriptor.sqlType == Types.BLOB) {
            BinaryTypeDescriptor.INSTANCE
        } else super.remapSqlTypeDescriptor(sqlTypeDescriptor)
    }
}