dbs=(
  # For bitcoin interpreter
  lama_btc
  # For IT tests
  test_lama
  test_lama_btc
  wd_cria_extra
)

for db in "${dbs[@]}"
do
  echo "Creating database $db"
  createdb $db -w -U $POSTGRES_USER
done
