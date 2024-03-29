// Copyright 2015-2023 Swim.inc
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

open module filethesebirds.munin {
  requires java.net.http;
  requires transitive java.sql;

  requires swim.server;

  requires org.commonmark;
  requires org.commonmark.ext.autolink;

  requires org.apache.commons.text;

  requires org.postgresql.jdbc;
  requires com.zaxxer.hikari;
}
