drop table EJB__TIMER__TBL;

create table EJB__TIMER__TBL (
CREATIONTIMERAW BIGINT NOT NULL,
BLOB  BLOB(2G),
TIMERID              VARCHAR(255) NOT NULL,
CONTAINERID          BIGINT       NOT NULL,
OWNERID              VARCHAR(255),
STATE                INTEGER      NOT NULL,
PKHASHCODE           INTEGER      NOT NULL,
INTERVALDURATION     BIGINT       NOT NULL,
INITIALEXPIRATIONRAW BIGINT       NOT NULL,
LASTEXPIRATIONRAW    BIGINT       NOT NULL,
CONSTRAINT PK_EJB__TIMER__TBL PRIMARY KEY (TIMERID)
);
