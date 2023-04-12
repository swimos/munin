CREATE TYPE taxon_category as ENUM('species', 'spuh', 'slash', 'issf', 'hybrid',
  'intergrade', 'domestic', 'form');

CREATE TYPE order_type as ENUM('struthioniformes', 'rheiformes', 'tinamiformes',
'casuariiformes', 'apterygiformes', 'anseriformes', 'galliformes',
'phoenicopteriformes', 'podicipediformes', 'columbiformes',
'mesitornithiformes', 'pterocliformes', 'otidiformes', 'musophagiformes',
'cuculiformes', 'caprimulgiformes', 'opisthocomiformes', 'gruiformes',
'charadriiformes', 'eurypygiformes', 'phaethontiformes', 'gaviiformes',
'sphenisciformes', 'procellariiformes', 'ciconiiformes', 'suliformes',
'pelecaniformes', 'cathartiformes', 'accipitriformes', 'strigiformes',
'coliiformes', 'leptosomiformes', 'trogoniformes', 'bucerotiformes',
'coraciiformes', 'galbuliformes', 'piciformes', 'cariamiformes',
'falconiformes', 'psittaciformes', 'passeriformes', 'unknown');

-- Full eBird taxonomy
CREATE TABLE taxa (
  tax_ordinal integer PRIMARY KEY CHECK (tax_ordinal > 0),
  tax_code text NOT NULL UNIQUE,
  tax_type taxon_category,
  com_name text NOT NULL, -- candidate for gin index
  sci_name text NOT NULL, -- candidate for gin index
  tax_species text,
  tax_genus text,
  tax_family text,
  tax_order order_type
);

-- FIXME: file location
COPY taxa FROM '/Users/rohitbose/brohitbrose/filethesebirds/seed/importable/taxa/taxa.csv'
  CSV HEADER;

CREATE INDEX taxa_tax_code_idx ON taxa (tax_code);
CREATE INDEX taxa_binomial_idx ON taxa (tax_genus, tax_species);

-- Ordinal bounds on every taxonomical order
CREATE TABLE tax_order_ordinals (
  tax_order order_type PRIMARY KEY,
  low integer,
  high integer
);

-- FIXME
COPY tax_order_ordinals FROM '/Users/rohitbose/brohitbrose/filethesebirds/seed/importable/taxa/tax_order_ordinals.csv'
  CSV HEADER;

-- Ordinal bounds on every taxonomical family
CREATE TABLE tax_family_ordinals (
  tax_family text PRIMARY KEY,
  low integer,
  high integer
);

-- FIXME
COPY tax_family_ordinals FROM '/Users/rohitbose/brohitbrose/filethesebirds/seed/importable/taxa/tax_family_ordinals.csv'
  CSV HEADER;

--------------------------------------------------------------------------------

CREATE TYPE location_type as ENUM('north america', 'latin america', 'europe',
  'africa', 'western asia', 'south asia', 'southeast asia', 'east asia',
  'australia/nz', 'central asia', 'pacific islands', 'middle east',
  'caribbean islands', 'private collection', 'unknown');

CREATE TYPE status_type as ENUM('unanswered', 'suggested', 'reviewed');

CREATE TABLE submissions (
  submission_id bigint PRIMARY KEY,
  location location_type,
  upload_date timestamp,
  karma int,
  comment_count int,
  title text,
  status status_type
);

CREATE TABLE observations (
  taxon_ordinal integer REFERENCES taxa (tax_ordinal),
  submission_id bigint REFERENCES submissions ON DELETE CASCADE,
  upload_date timestamp,
  PRIMARY KEY (taxon_ordinal, submission_id)
);
