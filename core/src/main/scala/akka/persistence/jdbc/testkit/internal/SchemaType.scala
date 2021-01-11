/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.testkit.internal

import akka.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi private[jdbc] sealed trait SchemaType

/**
 * INTERNAL API
 */
@InternalApi private[jdbc] case object Postgres extends SchemaType

/**
 * INTERNAL API
 */
@InternalApi private[jdbc] case object H2 extends SchemaType

/**
 * INTERNAL API
 */
@InternalApi private[jdbc] case object MySQL extends SchemaType

/**
 * INTERNAL API
 */
@InternalApi private[jdbc] case object Oracle extends SchemaType

/**
 * INTERNAL API
 */
@InternalApi private[jdbc] case object SqlServer extends SchemaType
